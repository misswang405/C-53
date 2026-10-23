package com.c53.modules.media.support;

import com.c53.common.ApiException;
import com.c53.config.MediaProperties;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 图片校验与重编码。
 *
 * <p>所有图片都会重新编码，天然剥离 EXIF/GPS；长边超过上限时等比缩放。
 * 带透明通道的 PNG 保留 PNG，其余统一输出 JPEG 并铺白底。</p>
 */
@Component
public class ImageProcessor {

    private final MediaProperties properties;

    public ImageProcessor(MediaProperties properties) {
        this.properties = properties;
    }

    /**
     * 处理结果。
     *
     * @param bytes  重编码后的字节
     * @param mime   输出 MIME
     * @param width  输出宽
     * @param height 输出高
     */
    public record ProcessedImage(byte[] bytes, String mime, int width, int height) {
    }

    /**
     * 解码、校验并重编码。
     *
     * @param raw     原始字节
     * @param mime    魔数识别出的 MIME
     * @return 处理结果
     */
    public ProcessedImage process(byte[] raw, String mime) {
        MediaProperties.Image config = properties.getImage();
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException exception) {
            throw invalid("图片解码失败");
        }
        if (source == null) {
            throw invalid("图片格式无法识别");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw invalid("图片尺寸异常");
        }
        if (width > config.getMaxSourceEdge() || height > config.getMaxSourceEdge()) {
            throw invalid("图片单边不能超过 " + config.getMaxSourceEdge() + " 像素");
        }

        boolean keepAlpha = "image/png".equals(mime) && source.getColorModel().hasAlpha();
        BufferedImage output = keepAlpha ? source : flattenOnWhite(source);

        int longEdge = Math.max(output.getWidth(), output.getHeight());
        int targetWidth = output.getWidth();
        int targetHeight = output.getHeight();
        boolean scaled = longEdge > config.getMaxOutputEdge();
        if (scaled) {
            double ratio = (double) config.getMaxOutputEdge() / longEdge;
            targetWidth = Math.max(1, (int) Math.round(output.getWidth() * ratio));
            targetHeight = Math.max(1, (int) Math.round(output.getHeight() * ratio));
        }

        try {
            Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(output);
            if (scaled) {
                builder.size(targetWidth, targetHeight);
            } else {
                builder.scale(1.0);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            builder.outputFormat(keepAlpha ? "png" : "jpg")
                    .outputQuality(config.getQuality())
                    .toOutputStream(buffer);
            return new ProcessedImage(buffer.toByteArray(), keepAlpha ? "image/png" : "image/jpeg",
                    targetWidth, targetHeight);
        } catch (IOException exception) {
            throw invalid("图片重编码失败");
        }
    }

    /**
     * 把带透明通道的图片铺到白底，避免 JPEG 出现黑底。
     */
    private BufferedImage flattenOnWhite(BufferedImage source) {
        BufferedImage canvas = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "MEDIA_INVALID", message);
    }
}
