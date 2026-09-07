import AudioToolbox
import FerventioShared
import Foundation
import UserNotifications

/// Native iOS endpoint for shared highlight side effects.
///
/// Shared Kotlin decides Ignore -> Highlight precedence exactly once. This bridge only performs
/// the requested platform side effects and never re-evaluates message rules.
final class HighlightAlertRuntimeBridge {
    func handle(alert: HighlightAlert) {
        if alert.push {
            let content = UNMutableNotificationContent()
            let channelLogin = alert.message.channelLogin.trimmingCharacters(in: .whitespacesAndNewlines)
            let author = alert.message.userDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
            if channelLogin.isEmpty {
                content.title = author.isEmpty ? "Ferventio" : author
            } else if author.isEmpty {
                content.title = "#\(channelLogin)"
            } else {
                content.title = "#\(channelLogin) · \(author)"
            }
            content.body = alert.message.text
            if alert.playSound {
                content.sound = .default
            }
            let request = UNNotificationRequest(
                identifier: "highlight-\(alert.message.id)",
                content: content,
                trigger: nil
            )
            UNUserNotificationCenter.current().add(request) { _ in }
            return
        }

        if alert.playSound {
            AudioServicesPlaySystemSound(1007)
        }
    }
}
