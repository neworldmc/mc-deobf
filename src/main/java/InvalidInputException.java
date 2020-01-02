public class InvalidInputException extends Exception {
    public int line;
    public String text;

    @Override
    public String getMessage() {
        return String.format("error at line %d: %s", line, text);
    }

    public InvalidInputException(int line, String text) {
        this.line = line;
        this.text = text;
    }
}