import Foundation

struct AppAboutConfiguration: Sendable, Equatable {
    let versionName: String
    let websiteURL: String
    let githubURL: String
    let telegramChannelURL: String
    let telegramChatURL: String
    let translationsURL: String
    let privacyOperatorName: String
    let privacyContact: String
    let privacyPolicyURL: String
    let showPrivacyPolicyInApp: Bool

    static func live(bundle: Bundle = .main) -> AppAboutConfiguration {
        AppAboutConfiguration(
            versionName: value(for: "CFBundleShortVersionString", in: bundle),
            websiteURL: value(for: "FerventioAppWebsiteURL", in: bundle),
            githubURL: value(for: "FerventioAppGitHubURL", in: bundle),
            telegramChannelURL: value(for: "FerventioAppTelegramChannelURL", in: bundle),
            telegramChatURL: value(for: "FerventioAppTelegramChatURL", in: bundle),
            translationsURL: value(for: "FerventioAppTranslationsURL", in: bundle),
            privacyOperatorName: value(for: "FerventioPrivacyOperatorName", in: bundle),
            privacyContact: value(for: "FerventioPrivacyContact", in: bundle),
            privacyPolicyURL: value(for: "FerventioPrivacyPolicyURL", in: bundle),
            showPrivacyPolicyInApp: boolValue(for: "FerventioShowPrivacyPolicyInApp", in: bundle)
        )
    }

    private static func value(for key: String, in bundle: Bundle) -> String {
        (bundle.object(forInfoDictionaryKey: key) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private static func boolValue(for key: String, in bundle: Bundle) -> Bool {
        if let value = bundle.object(forInfoDictionaryKey: key) as? Bool {
            return value
        }
        if let value = bundle.object(forInfoDictionaryKey: key) as? NSNumber {
            return value.boolValue
        }
        switch value(for: key, in: bundle).lowercased() {
        case "1", "true", "yes": return true
        default: return false
        }
    }
}
