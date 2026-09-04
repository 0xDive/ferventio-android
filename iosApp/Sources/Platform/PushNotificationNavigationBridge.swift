import FerventioShared
import Foundation

struct PushNotificationNavigationPayload: Sendable {
    let channelID: String?
    let channelLogin: String?
    let messageID: String?
    let destination: String?

    init?(userInfo: [AnyHashable: Any]) {
        guard let payload = Self.dictionary(userInfo["ferventio"]) else {
            return nil
        }
        channelID = Self.nonEmptyString(payload["channelId"])
        channelLogin = Self.nonEmptyString(payload["channelLogin"])
        messageID = Self.nonEmptyString(payload["messageId"])
        destination = Self.nonEmptyString(payload["destination"])
    }

    private static func dictionary(_ value: Any?) -> [String: Any]? {
        if let dictionary = value as? [String: Any] {
            return dictionary
        }
        guard let dictionary = value as? [AnyHashable: Any] else {
            return nil
        }

        var result: [String: Any] = [:]
        result.reserveCapacity(dictionary.count)
        for (key, value) in dictionary {
            guard let key = key as? String else {
                continue
            }
            result[key] = value
        }
        return result
    }

    private static func nonEmptyString(_ value: Any?) -> String? {
        guard let value = value as? String else {
            return nil
        }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

@MainActor
final class PushNotificationNavigationBridge {
    private let inbox: PushNavigationInbox

    init(inbox: PushNavigationInbox) {
        self.inbox = inbox
    }

    @discardableResult
    func handle(_ payload: PushNotificationNavigationPayload) -> Bool {
        inbox.offer(
            channelId: payload.channelID,
            channelLogin: payload.channelLogin,
            messageId: payload.messageID,
            destination: payload.destination
        )
    }
}
