package org.arjunaoverdrive.app.webapp.controllers;

import org.arjunaoverdrive.app.services.ErrorService;
import org.arjunaoverdrive.app.webapp.DTO.ErrorDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/errors")
public class ErrorsController {
    @Autowired
    private final ErrorService errorService;

    public ErrorsController(ErrorService errorService) {
        this.errorService = errorService;
    }

    @GetMapping()
    public ResponseEntity listErrors(){
        ErrorDto errorDto = errorService.getErrorDto();
        return ResponseEntity.status(HttpStatus.OK).body(errorDto);
    }
}
