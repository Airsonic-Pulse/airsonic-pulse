package org.airsonic.player.upload;

import com.google.re2j.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;


public class MonitoredMultipartFile implements MultipartFile {

    private final static Pattern DOT_SLASH_PATTERN = Pattern.compile("\\.+/");

    private MultipartFile file;
    private UploadListener listener;

    public MonitoredMultipartFile(MultipartFile file, UploadListener listener) {
        this.file = file;
        this.listener = listener;

        if (this.getOriginalFilename() != null) {
            listener.start(FilenameUtils.getName(this.getOriginalFilename()));
        }
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public String getOriginalFilename() {
        if (Objects.isNull(file.getOriginalFilename())) {
            return null;
        }
        return DOT_SLASH_PATTERN.matcher(file.getOriginalFilename()).replaceAll("");
    }

    @Override
    public String getContentType() {
        return file.getContentType();
    }

    @Override
    public boolean isEmpty() {
        return file.isEmpty();
    }

    @Override
    public long getSize() {
        return file.getSize();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return file.getBytes();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return file.getInputStream();
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        try (InputStream inputStream = file.getInputStream()) {
            FileCopyUtils.copy(inputStream, new MonitoredOutputStream(new FileOutputStream(dest), listener));
        }
    }
}
