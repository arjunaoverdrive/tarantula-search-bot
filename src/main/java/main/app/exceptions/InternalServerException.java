package main.app.exceptions;

public class InternalServerException extends RuntimeException{
    private String message;

    public InternalServerException(String message) {
        super();
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
