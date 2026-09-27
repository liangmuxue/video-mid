package com.jizhi.videomid.uniview.nvr;

import com.jizhi.videomid.device.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecordDeviceService {
    private final RecordDeviceRepository repository;
    private final DeviceRepository deviceRepository;

    public RecordDeviceService(RecordDeviceRepository repository, DeviceRepository deviceRepository) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
    }

    public List<Map<String, Object>> list() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    public Map<String, Object> create(RecordDeviceRequest req) {
        RecordDevice d = fromRequest(req, null);
        if (repository.findOtherByLogin(d.getHost(), d.getPort(), null).isPresent()) {
            throw new IllegalArgumentException("该录像设备地址和端口已存在");
        }
        d.setId(repository.insert(d));
        return toView(d);
    }

    public Map<String, Object> update(Long id, RecordDeviceRequest req) {
        RecordDevice existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("录像设备不存在"));
        RecordDevice d = fromRequest(req, existing);
        d.setId(id);
        d.setCreatedAt(existing.getCreatedAt());
        if (repository.findOtherByLogin(d.getHost(), d.getPort(), id).isPresent()) {
            throw new IllegalArgumentException("该录像设备地址和端口已存在");
        }
        repository.update(d);
        return toView(repository.findById(id).orElse(d));
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new IllegalArgumentException("录像设备不存在");
        }
        if (deviceRepository.countByRecordDeviceId(id) > 0) {
            throw new IllegalArgumentException("仍有摄像机挂在这台录像设备上");
        }
        repository.deleteById(id);
    }

    public RecordDevice require(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("录像设备不存在"));
    }

    private RecordDevice fromRequest(RecordDeviceRequest req, RecordDevice existing) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String name = blank(req.getName());
        String host = blank(req.getHost());
        String username = blank(req.getUsername());
        if (name == null) {
            throw new IllegalArgumentException("录像设备名称不能为空");
        }
        if (host == null) {
            throw new IllegalArgumentException("录像设备地址不能为空");
        }
        if (req.getPort() == null || req.getPort() < 1 || req.getPort() > 65535) {
            throw new IllegalArgumentException("录像设备端口无效");
        }
        if (username == null) {
            throw new IllegalArgumentException("录像设备用户名不能为空");
        }
        String password = req.getPassword();
        if (password == null || password.isBlank()) {
            if (existing == null || existing.getPassword() == null || existing.getPassword().isBlank()) {
                throw new IllegalArgumentException("录像设备密码不能为空");
            }
            password = existing.getPassword();
        }
        RecordDevice d = new RecordDevice();
        d.setName(name);
        d.setHost(host);
        d.setPort(req.getPort());
        d.setUsername(username);
        d.setPassword(password);
        return d;
    }

    private Map<String, Object> toView(RecordDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("host", d.getHost());
        m.put("port", d.getPort());
        m.put("username", d.getUsername());
        return m;
    }

    private static String blank(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        return text.isEmpty() ? null : text;
    }
}
