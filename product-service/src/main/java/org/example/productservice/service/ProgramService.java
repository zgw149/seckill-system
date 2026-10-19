package org.example.productservice.service;

import org.example.productservice.dto.CreateProgramRequest;
import org.example.productservice.dto.UpdateProgramRequest;
import org.example.productservice.entity.Program;
import org.example.productservice.mapper.ProgramMapper;
import org.example.productservice.service.event.ProgramChangedEvent;
import org.example.productservice.service.event.ProgramEventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProgramService {
    @Autowired
    private ProgramMapper programMapper;

    @Autowired
    private ProgramEventProducer programEventProducer;
    // 创建节目
    @Transactional
    public Program createProgram(CreateProgramRequest request){
        checkCreateRequest(request);
        Program program = new Program();
        program.setTitle(request.title());
        program.setCity(request.city());
        program.setCategory(request.category());
        program.setVenue(request.venue());
        program.setDescription(request.description());
        program.setMinPrice(request.minPrice());
        program.setStock(request.stock());
        program.setHeat(request.heat() == null ? 0 : request.heat());
        program.setStartTime(request.startTime());
        programMapper.insert(program);
        // 发送节目变更事件
        Program savedProgram = programMapper.selectById(program.getId());
        sendUpsertEvent(savedProgram);
        return savedProgram;
    }
    @Transactional
    public Program updateProgram(Long id, UpdateProgramRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("节目 ID 不能为空");
        }
        checkUpdateRequest(request);
        Program oldProgram = programMapper.selectById(id);
        if (oldProgram == null) {
            throw new IllegalArgumentException("节目不存在，id=" + id);
        }
        Program program = new Program();
        program.setId(id);
        program.setTitle(request.title());
        program.setCity(request.city());
        program.setCategory(request.category());
        program.setVenue(request.venue());
        program.setDescription(request.description());
        program.setMinPrice(request.minPrice());
        program.setStock(request.stock());
        program.setHeat(request.heat() == null ? oldProgram.getHeat() : request.heat());
        program.setStartTime(request.startTime());
        programMapper.update(program);
        Program updatedProgram = programMapper.selectById(id);
        // 发送节目变更事件
        sendUpsertEvent(updatedProgram);
        return updatedProgram;
    }
    @Transactional
    public void deleteProgram(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("节目 ID 不能为空");
        }
        Program oldProgram = programMapper.selectById(id);
        if (oldProgram == null) {
            throw new IllegalArgumentException("节目不存在，id=" + id);
        }
        programMapper.deleteById(id);
        sendDeleteEvent(id);
    }
    public Program getProgram(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("节目 ID 不能为空");
        }
        Program program = programMapper.selectById(id);
        if (program == null) {
            throw new IllegalArgumentException("节目不存在，id=" + id);
        }
        return program;
    }
    public List<Program> listPrograms() {
        return programMapper.selectAll();
    }
    private void sendDeleteEvent(Long programId) {
        ProgramChangedEvent event = new ProgramChangedEvent(
                UUID.randomUUID().toString().replace("-", ""),
                "DELETE",
                programId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.now()
        );
        // 发送节目变更事件
        programEventProducer.sendProgramChangedEvent(event);
    }
    // 发送节目变更事件
    private void sendUpsertEvent(Program program) {
        ProgramChangedEvent event = new ProgramChangedEvent(
                UUID.randomUUID().toString().replace("-", ""),
                "UPSERT",
                program.getId(),
                program.getTitle(),
                program.getCity(),
                program.getCategory(),
                program.getVenue(),
                program.getDescription(),
                program.getMinPrice(),
                program.getStartTime(),
                program.getHeat(),
                LocalDateTime.now()
        );
        programEventProducer.sendProgramChangedEvent(event);
    }
    // 检查创建请求参数
    private void checkCreateRequest(CreateProgramRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("节目名称不能为空");
        }

        if (request.city() == null || request.city().isBlank()) {
            throw new IllegalArgumentException("城市不能为空");
        }

        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("分类不能为空");
        }

        if (request.venue() == null || request.venue().isBlank()) {
            throw new IllegalArgumentException("场馆不能为空");
        }

        if (request.minPrice() == null) {
            throw new IllegalArgumentException("最低票价不能为空");
        }

        if (request.stock() == null || request.stock() < 0) {
            throw new IllegalArgumentException("库存不能小于 0");
        }

        if (request.startTime() == null) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
    }
    // 检查更新请求参数
    private void checkUpdateRequest(UpdateProgramRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("节目名称不能为空");
        }

        if (request.city() == null || request.city().isBlank()) {
            throw new IllegalArgumentException("城市不能为空");
        }

        if (request.category() == null || request.category().isBlank()) {
            throw new IllegalArgumentException("分类不能为空");
        }

        if (request.venue() == null || request.venue().isBlank()) {
            throw new IllegalArgumentException("场馆不能为空");
        }

        if (request.minPrice() == null) {
            throw new IllegalArgumentException("最低票价不能为空");
        }

        if (request.stock() == null || request.stock() < 0) {
            throw new IllegalArgumentException("库存不能小于 0");
        }

        if (request.startTime() == null) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
    }

}
