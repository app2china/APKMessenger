package apkinfo;

import org.apache.commons.codec.binary.Hex;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class SigningCertificate {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx", Locale.ROOT);

    private final byte[] der;
    private final Map<String, String> identity;
    private final Map<String, String> publicKey;
    private final Map<String, String> signature;
    private final Map<String, String> fingerprints;

    public SigningCertificate(byte[] der) {
        this.der = der.clone();
        X509Certificate certificate = decode(this.der);
        if (certificate == null) {
            identity = publicKey = signature = fingerprints = Collections.emptyMap();
            return;
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("类型", certificate.getType());
        fields.put("证书版本", String.valueOf(certificate.getVersion()));
        fields.put("序列号", hex(certificate.getSerialNumber()));
        fields.put("主题 (Subject)", certificate.getSubjectX500Principal().getName());
        fields.put("颁发者 (Issuer)", certificate.getIssuerX500Principal().getName());
        DateTimeFormatter localDate = DATE_FORMAT.withZone(ZoneId.systemDefault());
        fields.put("有效期起始", localDate.format(certificate.getNotBefore().toInstant()));
        fields.put("有效期截止", localDate.format(certificate.getNotAfter().toInstant()));
        identity = Collections.unmodifiableMap(fields);
        publicKey = Collections.unmodifiableMap(describeKey(certificate.getPublicKey()));

        fields = new LinkedHashMap<>();
        fields.put("签名算法", certificate.getSigAlgName());
        fields.put("签名 OID", certificate.getSigAlgOID());
        signature = Collections.unmodifiableMap(fields);

        fields = new LinkedHashMap<>();
        for (String algorithm : new String[]{"SHA-256", "SHA-1", "MD5"}) {
            fields.put(algorithm, fingerprint(this.der, algorithm));
        }
        fingerprints = Collections.unmodifiableMap(fields);
    }

    public boolean isParsed() {
        return !identity.isEmpty();
    }

    public Map<String, String> getIdentity() {
        return identity;
    }

    public Map<String, String> getPublicKey() {
        return publicKey;
    }

    public Map<String, String> getSignature() {
        return signature;
    }

    public Map<String, String> getFingerprints() {
        return fingerprints;
    }

    public String getDerHex() {
        return Hex.encodeHexString(der);
    }

    public String getPem() {
        return "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
                + "\n-----END CERTIFICATE-----\n";
    }

    public String getReport() {
        StringJoiner report = new StringJoiner("\n\n");
        report.add(formatSection("证书信息", identity));
        report.add(formatSection("证书指纹", fingerprints));
        report.add(formatSection("公钥", publicKey));
        report.add(formatSection("证书签名算法", signature));
        return report.toString();
    }

    private static X509Certificate decode(byte[] der) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(der);
            X509Certificate certificate =
                    (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            return input.available() == 0 ? certificate : null;
        } catch (CertificateException e) {
            return null;
        }
    }

    private static Map<String, String> describeKey(PublicKey key) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("公钥类型", key.getAlgorithm());
        if (key instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) key;
            fields.put("指数", rsa.getPublicExponent().toString());
            fields.put("模数位数", rsa.getModulus().bitLength() + " bit");
            fields.put("模数", rsa.getModulus().toString());
        } else if (key instanceof ECPublicKey) {
            ECPublicKey ec = (ECPublicKey) key;
            if (ec.getParams() != null) {
                fields.put("曲线域位数", ec.getParams().getCurve().getField().getFieldSize() + " bit");
            }
            fields.put("公钥点 X", hex(ec.getW().getAffineX()));
            fields.put("公钥点 Y", hex(ec.getW().getAffineY()));
        } else if (key instanceof DSAPublicKey) {
            DSAPublicKey dsa = (DSAPublicKey) key;
            if (dsa.getParams() != null) {
                fields.put("参数位数", dsa.getParams().getP().bitLength() + " bit");
            }
            fields.put("公钥参数 Y", hex(dsa.getY()));
        }
        return fields;
    }

    private static String fingerprint(byte[] data, String algorithm) {
        try {
            String hex = Hex.encodeHexString(MessageDigest.getInstance(algorithm).digest(data))
                    .toUpperCase(Locale.ROOT);
            StringJoiner pairs = new StringJoiner(" ");
            for (int index = 0; index < hex.length(); index += 2) {
                pairs.add(hex.substring(index, index + 2));
            }
            return pairs.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing required digest algorithm: " + algorithm, e);
        }
    }

    private static String formatSection(String name, Map<String, String> fields) {
        StringJoiner rows = new StringJoiner("\n");
        rows.add(name);
        fields.forEach((key, value) -> rows.add(key + "：" + value));
        return rows.toString();
    }

    private static String hex(BigInteger value) {
        return "0x" + value.toString(16).toUpperCase(Locale.ROOT);
    }
}
