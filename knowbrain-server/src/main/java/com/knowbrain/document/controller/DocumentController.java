package com.knowbrain.document.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowbrain.common.Result;
import com.knowbrain.document.entity.EkDocument;
import com.knowbrain.document.service.DocumentService;
import com.knowbrain.permission.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PermissionService permissionService;

    /**
     * 上传文档（需 EDITOR 以上权限）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EkDocument> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam("spaceId") Long spaceId,
                                      @RequestParam(value = "category", required = false) String category,
                                      HttpServletRequest request) {
        if (file.isEmpty()) {
            return Result.badRequest("文件不能为空");
        }

        // 文件大小校验（≤ 50MB）
        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.badRequest("文件大小超过限制（最大 50MB），当前：" +
                    (file.getSize() / (1024 * 1024)) + "MB");
        }

        // 文件类型校验
        Set<String> allowedTypes = Set.of("pdf", "docx", "doc", "txt", "md");
        String fileName = file.getOriginalFilename();
        String ext = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : "";
        if (!allowedTypes.contains(ext)) {
            return Result.badRequest("不支持的文件类型（." + ext + "），仅允许：pdf, docx, doc, txt, md");
        }

        Long uploaderId = getUserId(request);
        permissionService.checkWriteAccess(spaceId, uploaderId);
        EkDocument doc = documentService.upload(file, spaceId, uploaderId, category);
        return Result.ok("上传成功", doc);
    }

    /**
     * 文档列表（分页，按空间过滤）
     */
    @GetMapping
    public Result<Page<EkDocument>> list(
            @RequestParam(value = "spaceId", required = false) Long spaceId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<EkDocument> result = documentService.listBySpace(spaceId, page, size);
        return Result.ok(result);
    }

    /**
     * 查询文档详情（需空间读权限）
     */
    @GetMapping("/{id}")
    public Result<EkDocument> getById(@PathVariable Long id, HttpServletRequest request) {
        EkDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        Long userId = getUserId(request);
        permissionService.checkReadAccess(doc.getSpaceId(), userId);
        return Result.ok(doc);
    }

    /**
     * 更新文档元数据（标题、分类）
     */
    @PutMapping("/{id}")
    public Result<Void> updateMeta(@PathVariable Long id,
                                   @RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        EkDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        Long userId = getUserId(request);
        permissionService.checkWriteAccess(doc.getSpaceId(), userId);
        String title = body.get("title");
        String category = body.get("category");
        documentService.updateMeta(id, title, category);
        return Result.ok("更新成功", null);
    }

    /**
     * 删除文档（需 EDITOR 以上权限）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        EkDocument doc = documentService.getById(id);
        if (doc == null) {
            return Result.notFound("文档不存在");
        }
        Long userId = getUserId(request);
        permissionService.checkWriteAccess(doc.getSpaceId(), userId);
        documentService.delete(id);
        return Result.ok("删除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Number ? ((Number) uid).longValue() : null;
    }
}
