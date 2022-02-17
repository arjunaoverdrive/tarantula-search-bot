package main.app.webapp.DTO;

public class ExceptionDto {
    private String error;

    public ExceptionDto(String error) {
        this.error = error;
    }

    public boolean isResult() {
        boolean result = false;
        return result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
