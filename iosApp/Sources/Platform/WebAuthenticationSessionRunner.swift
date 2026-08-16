import AuthenticationServices
import UIKit

@MainActor
final class WebAuthenticationSessionRunner: NSObject, ASWebAuthenticationPresentationContextProviding {
    enum Error: Swift.Error {
        case alreadyRunning
        case failedToStart
        case missingCallbackURL
    }

    private var activeSession: ASWebAuthenticationSession?
    private let fallbackAnchor = ASPresentationAnchor(frame: .zero)

    func authenticate(url: URL, callbackScheme: String) async throws -> URL {
        guard activeSession == nil else {
            throw Error.alreadyRunning
        }

        return try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: url,
                callbackURLScheme: callbackScheme
            ) { [weak self] callbackURL, error in
                Task { @MainActor in
                    self?.activeSession = nil
                    if let error {
                        continuation.resume(throwing: error)
                    } else if let callbackURL {
                        continuation.resume(returning: callbackURL)
                    } else {
                        continuation.resume(throwing: Error.missingCallbackURL)
                    }
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            activeSession = session
            guard session.start() else {
                activeSession = nil
                continuation.resume(throwing: Error.failedToStart)
                return
            }
        }
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) ?? fallbackAnchor
    }
}
