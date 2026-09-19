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
        guard let components = URLComponents(
            url: callbackURL,
            resolvingAgainstBaseURL: false
        ) else {
            throw Error.invalidCallbackURL
        }

        let queryItems = components.queryItems ?? []
        func values(named name: String) -> [String?] {
            queryItems
                .filter { $0.name == name }
                .map(\.value)
        }

        let nowEpochMillis = Int64(Date().timeIntervalSince1970 * 1_000)
        return evaluator.evaluateComponents(
            request: request,
            callbackScheme: components.scheme,
            callbackHost: components.host,
            callbackPath: components.path,
            callbackHasUserInfo: components.user != nil || components.password != nil,
            callbackFragment: components.fragment,
            callbackCodeValues: values(named: "code"),
            callbackStateValues: values(named: "state"),
            callbackErrorCodeValues: values(named: "error"),
            nowEpochMillis: nowEpochMillis
        )
    }
}
