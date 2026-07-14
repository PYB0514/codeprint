package application;

import org.springframework.scheduling.annotation.Async;

public class EmailService {
    @Async
    public void sendEmail(String msg) {
    }
}
