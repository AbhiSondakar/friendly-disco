package com.ecoloop;

import com.ecoloop.common.upload.FileStorageService;
import com.ecoloop.common.upload.UploadContentRepository;
import com.ecoloop.common.upload.UploadController;
import com.ecoloop.identity.User;
import com.ecoloop.identity.UserPrincipal;
import com.ecoloop.identity.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UploadStorageIntegrationTest {

    @Autowired
    private FileStorageService storageService;

    @Autowired
    private UploadContentRepository contentRepository;

    @Autowired
    private UploadController uploadController;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadContentPersistsAndRemainsOwnerProtected() throws Exception {
        User owner = createUser("owner");
        User other = createUser("other");
        byte[] image = createPng();

        FileStorageService.StoredFile stored = storageService.storeFile(
            owner.getId(),
            "devices",
            new MockMultipartFile("image", "device.png", "image/png", image),
            false
        );

        assertNull(stored.metadata().getStoragePath());
        assertArrayEquals(image, stored.content());
        assertArrayEquals(
            image,
            contentRepository.findById(stored.metadata().getId()).orElseThrow().getContent()
        );

        authenticate(owner);
        ResponseEntity<Resource> response = uploadController.download(stored.metadata().getId());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertArrayEquals(image, response.getBody().getInputStream().readAllBytes());

        authenticate(other);
        ResponseStatusException denied = assertThrows(
            ResponseStatusException.class,
            () -> uploadController.download(stored.metadata().getId())
        );
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    }

    private User createUser(String label) {
        return userRepository.save(new User(
            "upload-" + label + "-" + UUID.randomUUID() + "@example.test",
            "test-password-hash",
            "Upload " + label,
            "HOUSEHOLD"
        ));
    }

    private void authenticate(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities())
        );
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
