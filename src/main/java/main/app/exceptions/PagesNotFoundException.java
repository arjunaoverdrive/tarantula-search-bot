package main.app.exceptions;

public class PagesNotFoundException extends NullPointerException {
    private String message;
    public PagesNotFoundException(String s) {
        super();
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
