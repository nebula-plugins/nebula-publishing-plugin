package nebula.plugin.publishing;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyPacket;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/** Disposable fixture keys: no external GPG process, committed private key or user keyring. */
final class TestSigningKeys {
    private final BouncyCastleProvider provider = new BouncyCastleProvider();
    private final PGPPublicKey publicKey;

    TestSigningKeys(Path privateKeyFile) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = new JcaPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.RSA_SIGN,
                generator.generateKeyPair(), new Date(1577836800000L));
        var checksum = new JcaPGPDigestCalculatorProviderBuilder().setProvider(provider).build().get(HashAlgorithmTags.SHA1);
        var signer = new JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_SIGN, HashAlgorithmTags.SHA256).setProvider(provider);
        var key = new PGPSecretKey(PGPSignature.POSITIVE_CERTIFICATION, keyPair,
                "Publishing fixture <fixture@example.org>", checksum, null, null, signer, null);
        publicKey = key.getPublicKey();
        try (var output = new ArmoredOutputStream(Files.newOutputStream(privateKeyFile))) {
            key.encode(output);
        }
    }

    void verify(Path artifact) throws Exception {
        Path signatureFile = artifact.resolveSibling(artifact.getFileName() + ".asc");
        assertTrue(Files.exists(signatureFile), "Missing signature: " + signatureFile);
        try (var input = PGPUtil.getDecoderStream(Files.newInputStream(signatureFile))) {
            var signatures = assertInstanceOf(PGPSignatureList.class,
                    new PGPObjectFactory(input, new JcaKeyFingerprintCalculator()).nextObject());
            assertEquals(1, signatures.size());
            PGPSignature signature = signatures.get(0);
            assertEquals(publicKey.getKeyID(), signature.getKeyID());
            signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(provider), publicKey);
            signature.update(Files.readAllBytes(artifact));
            assertTrue(signature.verify(), "Invalid signature: " + signatureFile);
        }
    }
}
