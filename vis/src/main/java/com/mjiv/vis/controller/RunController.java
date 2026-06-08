package com.mjiv.vis.controller;

import com.mjiv.vis.interpreter.Interpreter;
import com.mjiv.vis.run.Runresult;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class RunController {

    @PostMapping("/run")
    public Runresult run(@RequestBody String code) {
        return new Interpreter().run(code);
    }

}
