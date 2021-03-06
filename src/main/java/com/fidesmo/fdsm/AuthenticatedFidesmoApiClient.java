/*
 * Copyright (c) 2018 - present Fidesmo AB
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.fidesmo.fdsm;

import apdu4j.HexUtils;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import pro.javacard.AID;
import pro.javacard.CAPFile;
import pro.javacard.CAPPackage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

public class AuthenticatedFidesmoApiClient extends FidesmoApiClient {
    private AuthenticatedFidesmoApiClient(String appId, String appKey) {
        super(appId, appKey);
    }

    public static AuthenticatedFidesmoApiClient getInstance(String appId, String appKey) throws IllegalArgumentException {
        return new AuthenticatedFidesmoApiClient(appId, appKey);
    }

    public void put(URI uri, String json) throws IOException {
        HttpPut put = new HttpPut(uri);
        put.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        transmit(put).close();
    }

    public void delete(URI uri) throws IOException {
        HttpDelete delete = new HttpDelete(uri);
        transmit(delete).close();
    }

    public String getAppId() {
        return appId;
    }


    // Upload a CAP file
    public void upload(CAPFile cap) throws IOException {
        if (cap.guessJavaCardVersion().equals("3.0.5")) {
            throw new IOException("Fidesmo supports JavaCard up to version 3.0.4");
        }

        HttpPost post = new HttpPost(getURI(ELF_URL));
        // Metadata headers
        post.setHeader("Java-Card-Version", cap.guessJavaCardVersion());
        // Do not send this info at this moment
        if (cap.guessGlobalPlatformVersion() != null) {
            String gpver = cap.guessGlobalPlatformVersion();
            // Always "upgrade" to (and verify against) 2.2
            if (gpver.equals("2.1.1"))
                gpver = "2.2";
            post.setHeader("Global-Platform-Version", gpver);
        }
        if (isJCOP242R2(cap)) {
            post.setHeader("OS-Type-Version", "JCOP 2.4.2r2");
        } else if (isJCOP242R1(cap)) {
            post.setHeader("OS-Type-Version", "JCOP 2.4.2r1");
        }

        // CAP content
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        cap.store(bos);
        post.setEntity(new ByteArrayEntity(bos.toByteArray()));
        transmit(post);
    }


    private static boolean isJCOPX(CAPFile cap, String version) {
        AID jcop = new AID(HexUtils.hex2bin("D276000085494A434F5058"));
        for (CAPPackage p : cap.getImports()) {
            if (p.getAid().equals(jcop) && p.getVersionString().equals(version))
                return true;
        }
        return false;
    }

    public static boolean isJCOP242R2(CAPFile cap) {
        // JC 3.0.1, GP 2.2.1, JCOPX 8.0
        return isJCOPX(cap, "8.0");
    }

    public static boolean isJCOP242R1(CAPFile cap) {
        // JC 3.0.1, GP 2.1.1, JCOPX 7.0
        return isJCOPX(cap, "7.0");
    }
}
