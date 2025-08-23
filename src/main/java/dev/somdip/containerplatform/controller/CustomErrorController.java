package dev.somdip.containerplatform.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    @ResponseBody
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute("javax.servlet.error.status_code");
        
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            
            if (statusCode == HttpStatus.NOT_FOUND.value()) {
                return "404 - Page not found";
            } else if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                return "500 - Internal server error";
            }
        }
        
        return "An error occurred";
    }
}