import FerventioShared
import Foundation
import UserNotifications

/// Native iOS endpoint for shared AutoMod hold side effects.
///
/// Shared Kotlin owns EventSub ordering and the AutoMod notification preference. This bridge only
/// presents the accepted hold and attaches the same moderation navigation payload as backend push.
final class AutoModAlertRuntimeBridge {
    func handle(message: AutoModHeldMessage) {
        let content = UNMutableNotificationContent()
        let userName = message.userName.trimmingCharacters(in: .whitespacesAndNewlines)
        content.title = userName.isEmpty ? "AutoMod" : "AutoMod: \(userName)"
        content.body = message.text
        content.sound = .default
        content.userInfo = [
            "ferventio": [
                "channelId": message.channelId,
                "channelLogin": message.channelLogin,
                "messageId": message.messageId,
                "destination": "moderation"
            ]
        ]

        let request = UNNotificationRequest(
            identifier: "automod-\(message.messageId)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request) { _ in }
    }
}
