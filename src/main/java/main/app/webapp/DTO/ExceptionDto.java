package main.app.webapp.DTO;

public class ExceptionDto {
    private final boolean result = false;
    private String message;

    public ExceptionDto(String message) {
        this.message = message;
    }

    public boolean isResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
