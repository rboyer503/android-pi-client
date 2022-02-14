package com.derelictvesseldev.pi_client;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

public class CryptoUtils {
    KeyStore keyStore;
    final String keyAlias = "CryptoKey";

    public CryptoUtils() {
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            if (!keyStore.containsAlias(keyAlias)) {
                // Key pair not created yet; create it now.
                KeyPairGenerator generator = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
                generator.initialize(new KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                        .build());
                generator.generateKeyPair();
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public String encryptString(String cleartext) {
        String ciphertext = "";

        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore
                    .getEntry(keyAlias, null);
            PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
            cipherOutputStream.write(cleartext.getBytes(StandardCharsets.UTF_8));
            cipherOutputStream.close();

            ciphertext = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ciphertext;
    }

    public String decryptString(String ciphertext) {
        String cleartext = "";

        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry)keyStore
                    .getEntry(keyAlias, null);
            PrivateKey privateKey = privateKeyEntry.getPrivateKey();

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            CipherInputStream cipherInputStream = new CipherInputStream(
                    new ByteArrayInputStream(Base64.decode(ciphertext, Base64.DEFAULT)), cipher);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte)nextByte);
            }

            byte[] bytes = new byte[values.size()];
            for(int i = 0; i < bytes.length; i++) {
                bytes[i] = values.get(i);
            }

            cleartext = new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cleartext;
    }
}