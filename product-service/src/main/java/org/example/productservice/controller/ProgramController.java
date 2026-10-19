package org.example.productservice.controller;

import org.example.productservice.dto.CreateProgramRequest;
import org.example.productservice.dto.UpdateProgramRequest;
import org.example.productservice.entity.Program;
import org.example.productservice.service.ProgramService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/programs")
public class ProgramController {
    @Autowired
    private ProgramService programService;
    @PostMapping
    public Program createProgram(@RequestBody CreateProgramRequest request) {
        return programService.createProgram(request);
    }

    @PutMapping("/{id}")
    public Program updateProgram(@PathVariable("id") Long id,
                                 @RequestBody UpdateProgramRequest request) {
        return programService.updateProgram(id, request);
    }

    @DeleteMapping("/{id}")
    public String deleteProgram(@PathVariable("id") Long id) {
        programService.deleteProgram(id);
        return "删除成功，id=" + id;
    }

    @GetMapping("/{id}")
    public Program getProgram(@PathVariable("id") Long id) {
        return programService.getProgram(id);
    }

    @GetMapping
    public List<Program> listPrograms() {
        return programService.listPrograms();
    }
}
