package application;

public class NotificationCaller {
    private EmailService emailService;

    public void notify(String msg) {
        emailService.sendEmail(msg);
    }
}
