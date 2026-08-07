#!/usr/bin/env python3
"""Generate XML resources and a Kotlin lookup table for legacy UI literals.

Every Cyrillic UI/status literal receives a stable hash-based Android string
resource. Runtime interpolation is represented with positional Android format
arguments, so the selected locale can translate both static and dynamic text.
"""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOTS = (
    ROOT / "app/src/main/kotlin",
    ROOT / "core/domain/src/main/kotlin",
    ROOT / "core/database/src/main/kotlin",
)
EN_TRANSLATIONS = ROOT / "config/localization/ui_strings_en.json"
OUT_EN = ROOT / "app/src/main/res/values/ui_strings_generated.xml"
OUT_RU = ROOT / "app/src/main/res/values-ru/ui_strings_generated.xml"
OUT_KT = ROOT / "app/src/main/kotlin/io/ferventio/app/ui/app/UiStringCatalog.generated.kt"

CYRILLIC_RE = re.compile(r"[А-Яа-яЁё]")


def decode_kotlin_string(raw: str) -> str:
    result: list[str] = []
    i = 0
    while i < len(raw):
        if raw[i] != "\\" or i + 1 >= len(raw):
            result.append(raw[i])
            i += 1
            continue
        nxt = raw[i + 1]
        decoded = {"n": "\n", "r": "\r", "t": "\t", '"': '"', "\\": "\\", "$": "$"}.get(nxt)
        if decoded is None:
            result.extend(("\\", nxt))
        else:
            result.append(decoded)
        i += 2
    return "".join(result)


def kotlin_literal(value: str) -> str:
    return json.dumps(value, ensure_ascii=False).replace("$", "\\$").replace("</", "<\\/")


TRANSLITERATION = str.maketrans({
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "h", "ц": "c", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
})


def resource_name(value: str) -> str:
    # Human-readable prefix helps translators navigate values-xx XML files while
    # the hash keeps names stable and collision-free when wording is similar.
    source = re.sub(r"\$\{.*?}|\$[A-Za-z_][A-Za-z0-9_.]*", " arg ", value.lower())
    source = source.translate(TRANSLITERATION)
    slug = re.sub(r"[^a-z0-9]+", "_", source).strip("_")[:52].rstrip("_") or "text"
    digest = hashlib.sha1(value.encode("utf-8")).hexdigest()[:8]
    return f"ui_{slug}_{digest}"


def template_parts(value: str) -> list[tuple[str, bool]]:
    """Return (text,is_argument), supporting balanced ${...} expressions."""
    parts: list[tuple[str, bool]] = []
    text_start = 0
    i = 0
    while i < len(value):
        if value[i] != "$" or i + 1 >= len(value):
            i += 1
            continue
        end: int | None = None
        if value[i + 1] == "{":
            depth = 1
            j = i + 2
            while j < len(value) and depth:
                if value[j] == "{": depth += 1
                elif value[j] == "}": depth -= 1
                j += 1
            if depth == 0:
                end = j
        elif value[i + 1].isalpha() or value[i + 1] == "_":
            j = i + 2
            while j < len(value) and (value[j].isalnum() or value[j] == "_"):
                j += 1
            while (
                j + 1 < len(value)
                and value[j] == "."
                and (value[j + 1].isalpha() or value[j + 1] == "_")
            ):
                j += 2
                while j < len(value) and (value[j].isalnum() or value[j] == "_"):
                    j += 1
            end = j
        if end is None:
            i += 1
            continue
        if text_start < i:
            parts.append((value[text_start:i], False))
        parts.append((value[i:end], True))
        text_start = end
        i = end
    if text_start < len(value):
        parts.append((value[text_start:], False))
    return parts


def argument_count(value: str) -> int:
    return sum(1 for _, is_argument in template_parts(value) if is_argument)


def format_template(value: str, source: str | None = None) -> str:
    """Convert Kotlin interpolation to positional Android format arguments.

    Translations may reorder or intentionally omit source arguments. Matching by the
    original interpolation expression preserves the correct source capture index.
    """
    parts = template_parts(value)
    if not any(is_arg for _, is_arg in parts):
        return value.replace("%", "%%") if source is not None and argument_count(source) else value

    source_value = source if source is not None else value
    source_arguments = [text for text, is_arg in template_parts(source_value) if is_arg]
    available: dict[str, list[int]] = {}
    for index, expression in enumerate(source_arguments, 1):
        available.setdefault(expression, []).append(index)

    out: list[str] = []
    for text, is_arg in parts:
        if not is_arg:
            out.append(text.replace("%", "%%"))
            continue
        indices = available.get(text)
        if not indices:
            raise SystemExit(
                f"translation uses unknown or duplicated placeholder {text!r} for {source_value!r}"
            )
        out.append(f"%{indices.pop(0)}$s")
    return "".join(out)


def regex_template(value: str) -> str | None:
    parts = template_parts(value)
    if not any(is_arg for _, is_arg in parts):
        return None
    out = ["^"]
    for text, is_arg in parts:
        out.append("(.*?)" if is_arg else re.escape(text))
    out.append("$")
    return "".join(out)


def xml_value(value: str) -> str:
    return escape(value, {'"': '&quot;'}).replace("'", "\\'")


def _consume_quoted(text: str, start: int, quote: str) -> tuple[str, int]:
    """Consume a Kotlin string, including quoted expressions inside ${...}."""
    raw = quote == '"""'
    i = start + len(quote)
    out: list[str] = []
    while i < len(text):
        if text.startswith(quote, i):
            return "".join(out), i + len(quote)
        if not raw and text[i] == "\\" and i + 1 < len(text):
            out.append(text[i : i + 2])
            i += 2
            continue
        if text.startswith("${", i):
            end = _consume_interpolation(text, i)
            out.append(text[i:end])
            i = end
            continue
        out.append(text[i])
        i += 1
    return "".join(out), i


def _consume_interpolation(text: str, start: int) -> int:
    depth = 1
    i = start + 2
    while i < len(text) and depth:
        if text.startswith('"""', i):
            _, i = _consume_quoted(text, i, '"""')
            continue
        if text[i] == '"':
            _, i = _consume_quoted(text, i, '"')
            continue
        if text[i] == "'":
            i += 1
            while i < len(text):
                if text[i] == "\\" and i + 1 < len(text):
                    i += 2
                elif text[i] == "'":
                    i += 1
                    break
                else:
                    i += 1
            continue
        if text.startswith("//", i):
            newline = text.find("\n", i + 2)
            i = len(text) if newline < 0 else newline + 1
            continue
        if text.startswith("/*", i):
            nesting = 1
            i += 2
            while i < len(text) and nesting:
                if text.startswith("/*", i):
                    nesting += 1
                    i += 2
                elif text.startswith("*/", i):
                    nesting -= 1
                    i += 2
                else:
                    i += 1
            continue
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1
    return i


def iter_kotlin_strings(text: str):
    i = 0
    while i < len(text):
        if text.startswith("//", i):
            newline = text.find("\n", i + 2)
            i = len(text) if newline < 0 else newline + 1
            continue
        if text.startswith("/*", i):
            nesting = 1
            i += 2
            while i < len(text) and nesting:
                if text.startswith("/*", i):
                    nesting += 1
                    i += 2
                elif text.startswith("*/", i):
                    nesting -= 1
                    i += 2
                else:
                    i += 1
            continue
        if text.startswith('"""', i):
            value, i = _consume_quoted(text, i, '"""')
            yield value
            continue
        if text[i] == '"':
            value, i = _consume_quoted(text, i, '"')
            yield decode_kotlin_string(value)
            continue
        if text[i] == "'":
            i += 1
            while i < len(text):
                if text[i] == "\\" and i + 1 < len(text):
                    i += 2
                elif text[i] == "'":
                    i += 1
                    break
                else:
                    i += 1
            continue
        i += 1


def collect() -> list[str]:
    values: set[str] = set()
    paths = sorted(
        path
        for source_root in SOURCE_ROOTS
        for path in source_root.rglob("*.kt")
        if "/test/" not in path.as_posix() and "/androidTest/" not in path.as_posix()
    )
    for path in paths:
        if path.name == "UiStringCatalog.generated.kt":
            continue
        text = path.read_text(encoding="utf-8")
        for value in iter_kotlin_strings(text):
            if CYRILLIC_RE.search(value):
                values.add(value)
    return sorted(values, key=lambda item: (item.casefold(), item))


def load_translations() -> dict[str, str]:
    if not EN_TRANSLATIONS.exists():
        return {}
    payload = json.loads(EN_TRANSLATIONS.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise SystemExit(f"{EN_TRANSLATIONS} must contain a JSON object")
    return {str(k): str(v) for k, v in payload.items()}


def write_xml(path: Path, values: list[str], translated: dict[str, str] | None) -> None:
    lines = ["<resources>", "    <!-- Generated by scripts/localization/generate_ui_catalog.py. -->"]
    for value in values:
        resolved = translated.get(value, value) if translated is not None else value
        if argument_count(value):
            resolved = format_template(resolved, source=value)
        lines.append(f'    <string name="{resource_name(value)}">{xml_value(resolved)}</string>')
    lines.append("</resources>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_kotlin(path: Path, values: list[str]) -> None:
    fixed = [value for value in values if regex_template(value) is None]
    patterns = [(value, regex_template(value)) for value in values if regex_template(value) is not None]
    patterns.sort(
        key=lambda item: (
            -sum(len(text) for text, is_argument in template_parts(item[0]) if not is_argument),
            argument_count(item[0]),
            item[0],
        )
    )
    lines = [
        "package io.ferventio.app.ui",
        "",
        "import android.content.Context",
        "import io.ferventio.app.R",
        "",
        "/** Generated by scripts/localization/generate_ui_catalog.py. */",
        "internal object UiStringCatalog {",
        "    private data class PatternResource(val pattern: Regex, val resourceId: Int)",
        "",
        "    private const val VERBATIM_OPEN = \"\uE000\"",
        "    private const val VERBATIM_CLOSE = \"\uE001\"",
        "",
        "    fun verbatim(value: String): String = buildString(value.length + 2) {",
        "        append(VERBATIM_OPEN)",
        "        append(value.replace(VERBATIM_OPEN, VERBATIM_OPEN + VERBATIM_OPEN))",
        "        append(VERBATIM_CLOSE)",
        "    }",
        "",
        "    private fun unwrapVerbatim(value: String): String =",
        "        if (value.startsWith(VERBATIM_OPEN) && value.endsWith(VERBATIM_CLOSE)) {",
        "            value.substring(1, value.length - 1)",
        "                .replace(VERBATIM_OPEN + VERBATIM_OPEN, VERBATIM_OPEN)",
        "        } else {",
        "            value",
        "        }",
        "",
        "    private val resourceBySource: Map<String, Int> = mapOf(",
    ]
    for value in fixed:
        lines.append(f"        {kotlin_literal(value)} to R.string.{resource_name(value)},")
    lines.extend(["    )", "", "    private val patternResources: List<PatternResource> = listOf("])
    for value, regex in patterns:
        assert regex is not None
        lines.append(
            f"        PatternResource(Regex({kotlin_literal(regex)}), R.string.{resource_name(value)}),"
        )
    lines.extend([
        "    )",
        "",
        "    fun resolve(context: Context, source: String): String = resolve(context, source, depth = 0)",
        "",
        "    private fun resolve(context: Context, source: String, depth: Int): String {",
        "        if (source.startsWith(VERBATIM_OPEN) && source.endsWith(VERBATIM_CLOSE)) {",
        "            return unwrapVerbatim(source)",
        "        }",
        "        if (depth >= 8) return source",
        "        resourceBySource[source]?.let { return context.getString(it) }",
        "        patternResources.forEach { candidate ->",
        "            val match = candidate.pattern.matchEntire(source) ?: return@forEach",
        "            val arguments = match.groupValues.drop(1)",
        "                .map { argument -> unwrapVerbatim(argument) }",
        "                .toTypedArray()",
        "            return context.getString(candidate.resourceId, *arguments)",
        "        }",
        '        if (" · " in source) {',
        '            val parts = source.split(" · ")',
        '            val resolved = parts.map { part -> resolve(context, part, depth + 1) }',
        '            if (resolved != parts) return resolved.joinToString(" · ")',
        "        }",
        "        return source",
        "    }",
        "}",
    ])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    values = collect()
    translations = load_translations()
    write_xml(OUT_RU, values, None)
    write_xml(OUT_EN, values, translations)
    write_kotlin(OUT_KT, values)
    missing = [value for value in values if value not in translations]
    print(
        f"generated {len(values)} strings; explicit English translations: "
        f"{len(values)-len(missing)}; fallback: {len(missing)}"
    )


if __name__ == "__main__":
    main()
