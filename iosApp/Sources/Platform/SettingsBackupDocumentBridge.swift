import Foundation
import UIKit
import UniformTypeIdentifiers

@MainActor
final class SettingsBackupDocumentBridge: NSObject, UIDocumentPickerDelegate {
    private enum Mode {
        case importing(onImport: (String) -> Void)
        case exporting(temporaryURL: URL, onExported: () -> Void)
    }

    private var mode: Mode?
    private var onFailure: ((String) -> Void)?
    private var onCancelled: (() -> Void)?

    func presentImport(
        onImport: @escaping (String) -> Void,
        onFailure: @escaping (String) -> Void
    ) {
        guard mode == nil else {
            onFailure("Another settings backup operation is already open")
            return
        }
        guard let presenter = activePresenter() else {
            onFailure("Unable to present the settings backup picker")
            return
        }

        mode = .importing(onImport: onImport)
        self.onFailure = onFailure
        let picker = UIDocumentPickerViewController(
            forOpeningContentTypes: [.json, .plainText, .data],
            asCopy: true
        )
        picker.allowsMultipleSelection = false
        picker.delegate = self
        presenter.present(picker, animated: true)
    }

    func presentExport(
        content: String,
        onExported: @escaping () -> Void,
        onCancelled: @escaping () -> Void,
        onFailure: @escaping (String) -> Void
    ) {
        guard mode == nil else {
            onFailure("Another settings backup operation is already open")
            return
        }
        guard let presenter = activePresenter() else {
            onFailure("Unable to present the settings backup picker")
            return
        }

        do {
            let temporaryURL = try writeTemporaryBackup(content)
            mode = .exporting(temporaryURL: temporaryURL, onExported: onExported)
            self.onFailure = onFailure
            self.onCancelled = onCancelled
            let picker = UIDocumentPickerViewController(
                forExporting: [temporaryURL],
                asCopy: true
            )
            picker.delegate = self
            presenter.present(picker, animated: true)
        } catch {
            cleanupOperation()
            onFailure(String(describing: error))
        }
    }

    func documentPicker(
        _ controller: UIDocumentPickerViewController,
        didPickDocumentsAt urls: [URL]
    ) {
        guard let mode else { return }
        switch mode {
        case let .importing(onImport):
            guard let url = urls.first else {
                finishFailure("No settings backup file was selected")
                return
            }
            do {
                onImport(try readBackup(url))
                cleanupOperation()
            } catch {
                finishFailure(String(describing: error))
            }
        case let .exporting(_, onExported):
            onExported()
            cleanupOperation()
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onCancelled?()
        cleanupOperation()
    }

    private func readBackup(_ url: URL) throws -> String {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed {
                url.stopAccessingSecurityScopedResource()
            }
        }
        let data = try Data(contentsOf: url, options: [.mappedIfSafe])
        guard data.count <= Self.maximumBackupBytes else {
            throw SettingsBackupDocumentError.fileTooLarge
        }
        guard let raw = String(data: data, encoding: .utf8) else {
            throw SettingsBackupDocumentError.invalidUtf8
        }
        return raw
    }

    private func writeTemporaryBackup(_ content: String) throws -> URL {
        guard let data = content.data(using: .utf8) else {
            throw SettingsBackupDocumentError.invalidUtf8
        }
        guard data.count <= Self.maximumBackupBytes else {
            throw SettingsBackupDocumentError.fileTooLarge
        }
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("FerventioSettingsBackups", isDirectory: true)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true
        )
        let timestamp = Self.fileTimestampFormatter.string(from: Date())
        let url = directory.appendingPathComponent("ferventio-settings-\(timestamp).json")
        try data.write(to: url, options: [.atomic])
        return url
    }

    private func finishFailure(_ message: String) {
        let callback = onFailure
        cleanupOperation()
        callback?(message)
    }

    private func cleanupOperation() {
        if case let .exporting(temporaryURL, _)? = mode {
            try? FileManager.default.removeItem(at: temporaryURL)
        }
        mode = nil
        onFailure = nil
        onCancelled = nil
    }

    private func activePresenter() -> UIViewController? {
        let window = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .filter { $0.activationState == .foregroundActive }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)
        guard let root = window?.rootViewController else { return nil }
        return topViewController(root)
    }

    private func topViewController(_ root: UIViewController) -> UIViewController {
        if let presented = root.presentedViewController {
            return topViewController(presented)
        }
        if let navigation = root as? UINavigationController,
           let visible = navigation.visibleViewController {
            return topViewController(visible)
        }
        if let tabs = root as? UITabBarController,
           let selected = tabs.selectedViewController {
            return topViewController(selected)
        }
        return root
    }

    private static let maximumBackupBytes = 1_048_576
    private static let fileTimestampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyyMMdd-HHmmss"
        return formatter
    }()
}

private enum SettingsBackupDocumentError: LocalizedError {
    case fileTooLarge
    case invalidUtf8

    var errorDescription: String? {
        switch self {
        case .fileTooLarge:
            return "Settings backup exceeds the 1 MiB limit"
        case .invalidUtf8:
            return "Settings backup is not valid UTF-8"
        }
    }
}
