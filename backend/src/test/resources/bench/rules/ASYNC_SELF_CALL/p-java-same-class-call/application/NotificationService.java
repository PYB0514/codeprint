package application;

import org.springframework.scheduling.annotation.Async;

public class NotificationService {
    public void notify(String msg) {
        sendEmail(msg);
    }

    @Async
    public void sendEmail(String msg) {
    }
}
