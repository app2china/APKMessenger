package ToolUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SignUtil {
    private static final int V2_BLOCK_ID = 0x7109871a;
    private static final int V3_BLOCK_ID = 0xf05368c0;
    private static final int MAX_CERTIFICATE_SIZE = 1024 * 1024;

    /**
     * 从APK中读取签名
     * @param file
     * @return
     * @throws IOException
     */
    public static List<String> getSignaturesFromApk(File file) throws IOException {
        return getSignaturesFromApk(file, 1);
    }

    public static List<String> getSignaturesFromApk(File file, int version) throws IOException {
        List<String> signatures = new ArrayList<>();
        for (byte[] certificate : readCertificates(file, version)) {
            signatures.add(toCharsString(certificate));
        }
        return signatures;
    }

    private static List<byte[]> readV1Certificates(File file) throws IOException {
        List<byte[]> signatures = new ArrayList<>();
        try (JarFile jarFile = new JarFile(file)) {
            JarEntry je=jarFile.getJarEntry("AndroidManifest.xml");
            if (je == null) {
                return signatures;
            }
            byte[] readBuffer=new byte[8192];
            Certificate[] certs=loadCertificates(jarFile, je, readBuffer);
            if(certs != null) {
                for(Certificate c: certs) {
                    signatures.add(c.getEncoded());
                }
            }
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IOException("Unable to encode APK signing certificate", e);
        }
        return signatures;
    }

    /**
     * Extracts the certificates embedded in the requested signing scheme.
     */
    public static List<byte[]> readCertificates(File file, int version) throws IOException {
        if (version == 1) {
            return readV1Certificates(file);
        }
        if (version != 2 && version != 3) {
            throw new IllegalArgumentException("Unsupported APK signature scheme: " + version);
        }

        List<byte[]> certificates = new ArrayList<>();
        try (RandomAccessFile apk = new RandomAccessFile(file, "r")) {
            long centralDirectory = findCentralDirectory(apk);
            if (centralDirectory < 24) {
                return certificates;
            }

            long footer = centralDirectory - 24;
            apk.seek(footer);
            long blockSize = Long.reverseBytes(apk.readLong());
            // The footer ends with the ASCII magic "APK Sig Block 42".
            if (apk.readLong() != 0x41504b2053696720L || apk.readLong() != 0x426c6f636b203432L) {
                return certificates;
            }
            if (blockSize < 24 || blockSize > centralDirectory - 8) {
                throw new IOException("Invalid APK signing block size");
            }
            apk.seek(centralDirectory - blockSize - 8);
            if (Long.reverseBytes(apk.readLong()) != blockSize) {
                throw new IOException("APK signing block sizes disagree");
            }

            while (apk.getFilePointer() < footer) {
                if (footer - apk.getFilePointer() < 8) {
                    throw new IOException("Truncated APK signing block entry");
                }
                long entrySize = Long.reverseBytes(apk.readLong());
                if (entrySize < 4 || entrySize > footer - apk.getFilePointer()) {
                    throw new IOException("Invalid APK signing block entry size");
                }
                long entryEnd = apk.getFilePointer() + entrySize;
                int id = Integer.reverseBytes(apk.readInt());
                if (id == (version == 2 ? V2_BLOCK_ID : V3_BLOCK_ID)) {
                    readSignerCertificates(apk, entryEnd, certificates);
                }
                apk.seek(entryEnd);
            }
        }
        return certificates;
    }

    private static long findCentralDirectory(RandomAccessFile apk) throws IOException {
        int minimumEocdSize = 22;
        int tailSize = (int) Math.min(apk.length(), minimumEocdSize + 0xffff);
        long tailStart = apk.length() - tailSize;
        byte[] tail = new byte[tailSize];
        apk.seek(tailStart);
        apk.readFully(tail);
        ByteBuffer zip = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);

        for (int index = tailSize - minimumEocdSize; index >= 0; index--) {
            if (zip.getInt(index) == 0x06054b50
                    && Short.toUnsignedInt(zip.getShort(index + 20)) == tailSize - index - minimumEocdSize) {
                long directorySize = Integer.toUnsignedLong(zip.getInt(index + 12));
                long directoryOffset = Integer.toUnsignedLong(zip.getInt(index + 16));
                if (directoryOffset + directorySize == tailStart + index) {
                    return directoryOffset;
                }
            }
        }
        throw new IOException("Could not locate APK ZIP central directory");
    }

    private static void readSignerCertificates(RandomAccessFile apk, long entryEnd, List<byte[]> certificates)
            throws IOException {
        long signersEnd = readLengthPrefixedEnd(apk, entryEnd);
        if (signersEnd == apk.getFilePointer()) {
            throw new IOException("APK signing scheme contains no signers");
        }
        while (apk.getFilePointer() < signersEnd) {
            long signerEnd = readLengthPrefixedEnd(apk, signersEnd);
            long signedDataEnd = readLengthPrefixedEnd(apk, signerEnd);
            // Both schemes begin signed data with the digest sequence, then the certificates.
            apk.seek(readLengthPrefixedEnd(apk, signedDataEnd));
            long certificatesEnd = readLengthPrefixedEnd(apk, signedDataEnd);
            if (certificatesEnd == apk.getFilePointer()) {
                throw new IOException("APK signer contains no certificates");
            }
            while (apk.getFilePointer() < certificatesEnd) {
                long certificateEnd = readLengthPrefixedEnd(apk, certificatesEnd);
                long certificateSize = certificateEnd - apk.getFilePointer();
                if (certificateSize == 0 || certificateSize > MAX_CERTIFICATE_SIZE) {
                    throw new IOException("Invalid APK signing certificate size");
                }
                byte[] certificate = new byte[(int) certificateSize];
                apk.readFully(certificate);
                certificates.add(certificate);
            }
            apk.seek(signerEnd);
        }
    }

    private static long readLengthPrefixedEnd(RandomAccessFile apk, long limit) throws IOException {
        if (limit - apk.getFilePointer() < 4) {
            throw new IOException("Truncated APK signing field");
        }
        long size = Integer.toUnsignedLong(Integer.reverseBytes(apk.readInt()));
        if (size > limit - apk.getFilePointer()) {
            throw new IOException("APK signing field exceeds its enclosing field");
        }
        return apk.getFilePointer() + size;
    }

    /**
     * 加载签名
     * @param jarFile
     * @param je
     * @param readBuffer
     * @return
     */
    private static Certificate[] loadCertificates(JarFile jarFile, JarEntry je, byte[] readBuffer) throws IOException {
        try (InputStream is = jarFile.getInputStream(je)) {
            while(is.read(readBuffer, 0, readBuffer.length) != -1) {
            }
        }
        return je.getCertificates();
    }

    /**
     * 将签名转成转成可见字符串
     * @param sigBytes
     * @return
     */
    private static String toCharsString(byte[] sigBytes) {
        byte[] sig=sigBytes;
        final int N=sig.length;
        final int N2=N * 2;
        char[] text=new char[N2];
        for(int j=0; j < N; j++) {
            byte v=sig[j];
            int d=(v >> 4) & 0xf;
            text[j * 2]=(char)(d >= 10 ? ('a' + d - 10) : ('0' + d));
            d=v & 0xf;
            text[j * 2 + 1]=(char)(d >= 10 ? ('a' + d - 10) : ('0' + d));
        }
        return new String(text);
    }
}
