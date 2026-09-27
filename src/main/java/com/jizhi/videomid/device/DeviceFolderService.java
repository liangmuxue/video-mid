package com.jizhi.videomid.device;

import com.jizhi.videomid.device.dto.DeviceFolderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeviceFolderService {

    private final DeviceFolderRepository folderRepository;
    private final DeviceRepository deviceRepository;

    public DeviceFolderService(DeviceFolderRepository folderRepository,
                               DeviceRepository deviceRepository) {
        this.folderRepository = folderRepository;
        this.deviceRepository = deviceRepository;
    }

    public List<Map<String, Object>> tree() {
        List<DeviceFolder> all = folderRepository.findAll();
        Map<Long, Integer> counts = deviceRepository.countByFolderId();
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (DeviceFolder f : all) {
            Map<String, Object> n = toView(f);
            n.put("deviceCount", counts.getOrDefault(f.getId(), 0));
            n.put("children", new ArrayList<Map<String, Object>>());
            nodes.put(f.getId(), n);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (DeviceFolder f : all) {
            Map<String, Object> n = nodes.get(f.getId());
            if (f.getParentId() == null || !nodes.containsKey(f.getParentId())) {
                roots.add(n);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) nodes.get(f.getParentId()).get("children");
                children.add(n);
            }
        }
        // 汇总含子目录设备数
        for (Map<String, Object> root : roots) {
            rollupDeviceCount(root);
        }
        return roots;
    }

    @SuppressWarnings("unchecked")
    private int rollupDeviceCount(Map<String, Object> node) {
        int self = ((Number) node.getOrDefault("deviceCount", 0)).intValue();
        int sum = self;
        List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
        if (children != null) {
            for (Map<String, Object> c : children) {
                sum += rollupDeviceCount(c);
            }
        }
        node.put("totalDeviceCount", sum);
        return sum;
    }

    @Transactional
    public Map<String, Object> create(DeviceFolderRequest req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        Long parentId = req.getParentId();
        if (parentId != null) {
            folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("父目录不存在"));
        }
        DeviceFolder f = new DeviceFolder();
        f.setParentId(parentId);
        f.setName(name);
        f.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        f.setPath("");
        long id = folderRepository.insert(f);
        f.setId(id);
        String path = buildPath(parentId, id);
        folderRepository.updatePath(id, path);
        f.setPath(path);
        return toView(f);
    }

    @Transactional
    public Map<String, Object> update(Long id, DeviceFolderRequest req) {
        DeviceFolder existing = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("目录不存在"));
        String name = req.getName() == null ? existing.getName() : req.getName().trim();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("目录名称不能为空");
        }
        // parentId 以请求为准：null 表示移到根目录
        Long parentId = req.getParentId();
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new IllegalArgumentException("不能将目录设为自己的父级");
            }
            DeviceFolder parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("父目录不存在"));
            if (parent.getPath() != null && parent.getPath().contains("/" + id + "/")) {
                throw new IllegalArgumentException("不能将目录移动到其子目录下");
            }
        }
        String oldPath = existing.getPath() == null ? "" : existing.getPath();
        existing.setName(name);
        existing.setParentId(parentId);
        if (req.getSortNo() != null) {
            existing.setSortNo(req.getSortNo());
        }
        String newPath = buildPath(parentId, id);
        existing.setPath(newPath);
        folderRepository.update(existing);

        if (!oldPath.equals(newPath) && !oldPath.isEmpty()) {
            List<DeviceFolder> descendants = folderRepository.findByPathPrefix(oldPath);
            for (DeviceFolder d : descendants) {
                if (d.getId().equals(id)) continue;
                String p = d.getPath() == null ? "" : d.getPath();
                if (p.startsWith(oldPath)) {
                    folderRepository.updatePath(d.getId(), newPath + p.substring(oldPath.length()));
                }
            }
        }
        return toView(folderRepository.findById(id).orElse(existing));
    }

    @Transactional
    public void delete(Long id) {
        folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("目录不存在"));
        if (folderRepository.countChildren(id) > 0) {
            throw new IllegalArgumentException("请先删除子目录");
        }
        long devices = deviceRepository.countInFolder(id);
        if (devices > 0) {
            throw new IllegalArgumentException("目录下仍有设备，请先移动或删除设备");
        }
        folderRepository.deleteById(id);
    }

    /** 收集目录自身 + 全部子孙 id */
    public Set<Long> collectSelfAndDescendantIds(Long folderId) {
        Set<Long> ids = new HashSet<>();
        if (folderId == null) return ids;
        DeviceFolder f = folderRepository.findById(folderId).orElse(null);
        if (f == null) return ids;
        ids.add(folderId);
        String path = f.getPath();
        if (path != null && !path.isEmpty()) {
            for (DeviceFolder d : folderRepository.findByPathPrefix(path)) {
                ids.add(d.getId());
            }
        }
        return ids;
    }

    private String buildPath(Long parentId, long id) {
        if (parentId == null) {
            return "/" + id + "/";
        }
        DeviceFolder parent = folderRepository.findById(parentId).orElse(null);
        String prefix = parent == null || parent.getPath() == null || parent.getPath().isEmpty()
                ? "/" + parentId + "/"
                : parent.getPath();
        return prefix + id + "/";
    }

    private Map<String, Object> toView(DeviceFolder f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("parentId", f.getParentId());
        m.put("name", f.getName());
        m.put("sortNo", f.getSortNo());
        return m;
    }
}
