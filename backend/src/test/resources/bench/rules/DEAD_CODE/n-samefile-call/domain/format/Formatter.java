package domain.format;

public class Formatter {
    public String format(String input) {
        return trim(input);
    }

    private String trim(String input) {
        return input.trim();
    }
}
