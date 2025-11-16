package com.campus.panicbutton.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Utility class for image compression and optimization
 * Handles image resizing, quality adjustment, and format conversion for efficient storage and transmission
 */
object ImageCompressionUtils {
    
    private const val TAG = "ImageCompressionUtils"
    
    // Default compression settings
    private const val DEFAULT_MAX_WIDTH = 1024
    private const val DEFAULT_MAX_HEIGHT = 1024
    private const val DEFAULT_QUALITY = 80
    private const val MAX_FILE_SIZE_KB = 500 // 500KB max file size
    
    /**
     * Compression configuration
     */
    data class CompressionConfig(
        val maxWidth: Int = DEFAULT_MAX_WIDTH,
        val maxHeight: Int = DEFAULT_MAX_HEIGHT,
        val quality: Int = DEFAULT_QUALITY,
        val maxFileSizeKB: Int = MAX_FILE_SIZE_KB,
        val format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    )
    
    /**
     * Compression result
     */
    data class CompressionResult(
        val compressedFile: File,
        val originalSizeKB: Int,
        val compressedSizeKB: Int,
        val compressionRatio: Float,
        val width: Int,
        val height: Int
    )
    
    /**
     * Compress image from URI with default settings
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        outputFile: File? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        compressImage(context, imageUri, CompressionConfig(), outputFile)
    }
    
    /**
     * Compress image from URI with custom configuration
     */
    suspend fun compressImage(
        context: Context,
        imageUri: Uri,
        config: CompressionConfig,
        outputFile: File? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        
        val inputStream = context.contentResolver.openInputStream(imageUri)
            ?: throw IOException("Cannot open input stream for URI: $imageUri")
        
        val originalSize = inputStream.available()
        Log.d(TAG, "Original image size: ${originalSize / 1024}KB")
        
        // Decode bitmap with inSampleSize for memory efficiency
        val bitmap = decodeBitmapFromStream(inputStream, config.maxWidth, config.maxHeight)
        inputStream.close()
        
        // Apply rotation if needed
        val rotatedBitmap = rotateImageIfRequired(context, imageUri, bitmap)
        
        // Create output file if not provided
        val output = outputFile ?: createTempFile(context, "compressed_image", ".jpg")
        
        // Compress bitmap to file
        val compressedSize = compressBitmapToFile(rotatedBitmap, output, config)
        
        // Clean up bitmap
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        rotatedBitmap.recycle()
        
        val compressionRatio = if (originalSize > 0) {
            (originalSize - compressedSize).toFloat() / originalSize
        } else 0f
        
        Log.d(TAG, "Compression completed: ${originalSize / 1024}KB -> ${compressedSize / 1024}KB " +
                "(${(compressionRatio * 100).toInt()}% reduction)")
        
        CompressionResult(
            compressedFile = output,
            originalSizeKB = originalSize / 1024,
            compressedSizeKB = compressedSize / 1024,
            compressionRatio = compressionRatio,
            width = rotatedBitmap.width,
            height = rotatedBitmap.height
        )
    }
    
    /**
     * Compress image from file path
     */
    suspend fun compressImage(
        filePath: String,
        config: CompressionConfig = CompressionConfig(),
        outputFile: File? = null
    ): CompressionResult = withContext(Dispatchers.IO) {
        
        val inputFile = File(filePath)
        if (!inputFile.exists()) {
            throw IOException("Input file does not exist: $filePath")
        }
        
        val originalSize = inputFile.length().toInt()
        Log.d(TAG, "Original image size: ${originalSize / 1024}KB")
        
        // Decode bitmap with inSampleSize for memory efficiency
        val bitmap = decodeBitmapFromFile(filePath, config.maxWidth, config.maxHeight)
        
        // Apply rotation if needed
        val rotatedBitmap = rotateImageIfRequired(filePath, bitmap)
        
        // Create output file if not provided
        val output = outputFile ?: File(inputFile.parent, "compressed_${inputFile.name}")
        
        // Compress bitmap to file
        val compressedSize = compressBitmapToFile(rotatedBitmap, output, config)
        
        // Clean up bitmap
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        rotatedBitmap.recycle()
        
        val compressionRatio = if (originalSize > 0) {
            (originalSize - compressedSize).toFloat() / originalSize
        } else 0f
        
        Log.d(TAG, "Compression completed: ${originalSize / 1024}KB -> ${compressedSize / 1024}KB " +
                "(${(compressionRatio * 100).toInt()}% reduction)")
        
        CompressionResult(
            compressedFile = output,
            originalSizeKB = originalSize / 1024,
            compressedSizeKB = compressedSize / 1024,
            compressionRatio = compressionRatio,
            width = rotatedBitmap.width,
            height = rotatedBitmap.height
        )
    }
    
    /**
     * Decode bitmap from input stream with sample size calculation
     */
    private fun decodeBitmapFromStream(
        inputStream: InputStream,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        val bytes = inputStream.readBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
        
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Failed to decode bitmap from stream")
    }
    
    /**
     * Decode bitmap from file with sample size calculation
     */
    private fun decodeBitmapFromFile(
        filePath: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
        
        return BitmapFactory.decodeFile(filePath, options)
            ?: throw IOException("Failed to decode bitmap from file: $filePath")
    }
    
    /**
     * Calculate sample size for bitmap decoding
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        Log.d(TAG, "Original size: ${width}x${height}, Sample size: $inSampleSize")
        return inSampleSize
    }
    
    /**
     * Rotate image based on EXIF orientation
     */
    private fun rotateImageIfRequired(context: Context, imageUri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                return rotateBitmap(bitmap, orientation)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation", e)
        }
        return bitmap
    }
    
    /**
     * Rotate image based on EXIF orientation from file
     */
    private fun rotateImageIfRequired(filePath: String, bitmap: Bitmap): Bitmap {
        try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            return rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read EXIF orientation from file", e)
        }
        return bitmap
    }
    
    /**
     * Rotate bitmap based on orientation
     */
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Compress bitmap to file with quality adjustment
     */
    private fun compressBitmapToFile(
        bitmap: Bitmap,
        outputFile: File,
        config: CompressionConfig
    ): Int {
        var quality = config.quality
        var compressedSize: Int
        
        do {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(config.format, quality, outputStream)
            val compressedBytes = outputStream.toByteArray()
            compressedSize = compressedBytes.size
            
            if (compressedSize <= config.maxFileSizeKB * 1024 || quality <= 10) {
                // Write to file
                FileOutputStream(outputFile).use { fileOut ->
                    fileOut.write(compressedBytes)
                }
                break
            }
            
            // Reduce quality and try again
            quality -= 10
            Log.d(TAG, "File size ${compressedSize / 1024}KB exceeds limit, reducing quality to $quality")
            
        } while (quality > 0)
        
        return compressedSize
    }
    
    /**
     * Create temporary file for compression
     */
    private fun createTempFile(context: Context, prefix: String, suffix: String): File {
        val cacheDir = File(context.cacheDir, "compressed_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File.createTempFile(prefix, suffix, cacheDir)
    }
    
    /**
     * Get image dimensions without loading full bitmap
     */
    fun getImageDimensions(context: Context, imageUri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }
        
        return Pair(options.outWidth, options.outHeight)
    }
    
    /**
     * Get image dimensions from file
     */
    fun getImageDimensions(filePath: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        BitmapFactory.decodeFile(filePath, options)
        return Pair(options.outWidth, options.outHeight)
    }
    
    /**
     * Clean up compressed images cache
     */
    fun cleanupCache(context: Context) {
        val cacheDir = File(context.cacheDir, "compressed_images")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            Log.d(TAG, "Compressed images cache cleaned up")
        }
    }
}