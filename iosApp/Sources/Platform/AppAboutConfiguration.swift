import Foundation

struct AppAboutConfiguration: Sendable, Equatable {
    let versionName: String
    let websiteURL: String
    let githubURL: String
    let telegramChannelURL: String
    let telegramChatURL: String
    let translationsURL: String

    static func live(bundle: Bundle = .main) -> AppAboutConfiguration {
        AppAboutConfiguration(
            versionName: value(for: "CFBundleShortVersionString", in: bundle),
            websiteURL: value(for: "FerventioAppWebsiteURL", in: bundle),
            githubURL: value(for: "FerventioAppGitHubURL", in: bundle),
            telegramChannelURL: value(for: "FerventioAppTelegramChannelURL", in: bundle),
            telegramChatURL: value(for: "FerventioAppTelegramChatURL", in: bundle),
            translationsURL: value(for: "FerventioAppTranslationsURL", in: bundle)
        )
    }

    private static func value(for key: String, in bundle: Bundle) -> String {
        (bundle.object(forInfoDictionaryKey: key) as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
}
