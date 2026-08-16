import FerventioShared
import Foundation

@MainActor
final class BackendAuthorizationSessionAdapter {
    enum Error: Swift.Error {
        case invalidAuthorizationURL
        case invalidCallbackURL
    }

    private let runner: WebAuthenticationSessionRunner
    private let evaluator: BackendAuthorizationCallbackEvaluator

    init(
        runner: WebAuthenticationSessionRunner = WebAuthenticationSessionRunner(),
        evaluator: BackendAuthorizationCallbackEvaluator = BackendAuthorizationCallbackEvaluator()
    ) {
        self.runner = runner
        self.evaluator = evaluator
    }

    func authenticate(
        request: BackendAuthorizationBrowserRequest
    ) async throws -> BackendAuthorizationCallbackResult {
        guard
            !request.callbackScheme.isEmpty,
            let authorizationURL = URL(string: request.authorizationUrl)
        else {
            throw Error.invalidAuthorizationURL
        }

        let callbackURL = try await runner.authenticate(
            url: authorizationURL,
            callbackScheme: request.callbackScheme
        )
        guard
            callbackURL.scheme?.caseInsensitiveCompare(request.callbackScheme) == .orderedSame,
            let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)
        else {
            throw Error.invalidCallbackURL
        }

        let queryItems = components.queryItems ?? []
        func value(named name: String) -> String? {
            queryItems.first(where: { $0.name == name })?.value
        }

        let nowEpochMillis = Int64(Date().timeIntervalSince1970 * 1_000)
        return evaluator.evaluate(
            request: request,
            callbackCode: value(named: "code"),
            callbackState: value(named: "state"),
            callbackErrorCode: value(named: "error"),
            nowEpochMillis: nowEpochMillis
        )
    }
}
