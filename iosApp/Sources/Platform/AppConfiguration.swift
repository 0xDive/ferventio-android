import Foundation

struct AppConfiguration: Sendable {
    enum Error: Swift.Error, Equatable {
        case missingServerURL
        case missingCallbackScheme
    }

    let serverURL: String
    let callbackScheme: String
    let keychainService: String

    static func live(bundle: Bundle = .main) throws -> AppConfiguration {
        guard let rawServerURL = bundle.object(
            forInfoDictionaryKey: "FerventioServerURL"
        ) as? String else {
            throw Error.missingServerURL
        }
        let serverURL = rawServerURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !serverURL.isEmpty else {
            throw Error.missingServerURL
        }

        let callbackScheme = bundle.bundleIdentifier?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let callbackScheme, !callbackScheme.isEmpty else {
            throw Error.missingCallbackScheme
        }

        return AppConfiguration(
            serverURL: serverURL,
            callbackScheme: callbackScheme,
            keychainService: callbackScheme
        )
    }
}
