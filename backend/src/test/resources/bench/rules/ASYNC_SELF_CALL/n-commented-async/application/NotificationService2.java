package application;

public class NotificationService2 {
    public void notify(String msg) {
        sendEmail(msg);
    }

    // @Async
    public void sendEmail(String msg) {
    }
}
