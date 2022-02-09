package main.app.webapp.controllers;

import main.app.exceptions.InternalServerException;
import main.app.exceptions.PagesNotFoundException;
import main.app.webapp.DTO.ExceptionDto;
import org.apache.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class ExceptionController {
    private final Logger LOGGER = Logger.getLogger(ExceptionController.class);

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class})
    public ResponseEntity handleError400(Exception e){
        String message = e instanceof MissingServletRequestParameterException ?
                "Отсутствует обязательный параметр в запросе " + ((MissingServletRequestParameterException) e).getParameterName()
                : e.getLocalizedMessage();
        ExceptionDto error = new ExceptionDto(message);
        LOGGER.warn(error.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler( PagesNotFoundException.class)
    public ResponseEntity handleError404(PagesNotFoundException e){
        ExceptionDto error = new ExceptionDto(e.getLocalizedMessage());
        LOGGER.warn(error.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity handleError405(HttpServletRequest request) {
        String method = request.getMethod();
        ExceptionDto error = new ExceptionDto("Метод " + method + " не разрешён");
        LOGGER.warn(error.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity handleError403(RuntimeException e){
        ExceptionDto error = new ExceptionDto(e.getLocalizedMessage());
        LOGGER.warn(error.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler({Exception.class, InternalServerException.class})
    public ResponseEntity handleError500(Exception e){
        ExceptionDto error = new ExceptionDto(e.getLocalizedMessage());
        LOGGER.warn(error.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }





}
