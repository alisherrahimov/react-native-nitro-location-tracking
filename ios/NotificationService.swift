import UserNotifications

class NotificationService {

    init() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]) { _, _ in }
    }

    func showLocalNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    func updateForegroundNotification(title: String, body: String) {
        // iOS doesn't have a persistent foreground service notification
        // like Android. This is a no-op on iOS — background tracking
        // is handled via CoreLocation background mode.
        // If needed, we show a local notification instead.
        showLocalNotification(title: title, body: body)
    }
}
