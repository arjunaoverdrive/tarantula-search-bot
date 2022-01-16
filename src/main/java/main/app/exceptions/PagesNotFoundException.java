package main.app.exceptions;

public class PagesNotFoundException extends Throwable {
    private String message;
    public PagesNotFoundException(String s) {
        this.message  = s;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
