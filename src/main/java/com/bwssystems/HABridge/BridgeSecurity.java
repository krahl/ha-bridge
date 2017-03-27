package com.bwssystems.HABridge;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class BridgeSecurity {
	private static final Logger log = LoggerFactory.getLogger(BridgeSecurity.class);
	private char[] habridgeKey;
    private static final byte[] SALT = {
            (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12,
            (byte) 0xde, (byte) 0x33, (byte) 0x10, (byte) 0x12,
        };
    private BridgeSecurityDescriptor securityDescriptor;
	private boolean settingsChanged;

	public BridgeSecurity(char[] theKey, String theData) {
		habridgeKey = theKey;
		securityDescriptor = null;
		settingsChanged = false;
		String anError = null;
		if(theData != null && !theData.isEmpty()) {
			try {
				securityDescriptor = new Gson().fromJson(decrypt(theData), BridgeSecurityDescriptor.class);
			} catch (JsonSyntaxException e) {
				anError = e.getMessage();
			} catch (GeneralSecurityException e) {
				anError = e.getMessage();
			} catch (IOException e) {
				anError = e.getMessage();
			}
			log.warn("Cound not get security data, using default security (none): " + anError);
		}
		
		if(theData == null || anError != null) {
			securityDescriptor = new BridgeSecurityDescriptor();
		}
	}

	public String getSecurityDescriptorData() throws UnsupportedEncodingException, GeneralSecurityException {
		return encrypt(new Gson().toJson(securityDescriptor));
	}
	
	public boolean isUseLinkButton() {
		return securityDescriptor.isUseLinkButton();
	}

	public String setPassword(User aUser) throws IOException {
		String error = null;
		if(aUser != null) {
			error = aUser.validate();
			if(error == null) {
				if(securityDescriptor.getUsers() != null) {
					User theUser = securityDescriptor.getUsers().get(aUser.getUsername());
					if(theUser != null) {
						theUser.setPassword(aUser.getPassword());
						theUser.setPassword2(null);
						settingsChanged = true;
					}
					else
						error = "User not found";
				}
				else
					error = "User not found";
			}
		}
		else
			error = "invalid user object given";
		
		return error;
	}

	public String addUser(User aUser) throws IOException {
		String error = null;
		if(aUser != null) {
			error = aUser.validate();
			if(error == null) {
				if(securityDescriptor.getUsers() == null)
					securityDescriptor.setUsers(new HashMap<String, User>());
				if(securityDescriptor.getUsers().get(aUser.getUsername()) == null) {
					securityDescriptor.getUsers().put(aUser.getUsername(), aUser);
					settingsChanged = true;
				}
				else
					error = "Invalid request";
			}
		}
		else
			error = "invalid user object given";
		
		return error;
	}

	public void setExecGarden(String theGarden) {
		securityDescriptor.setExecGarden(theGarden);
		settingsChanged = true;
	}

	public String getExecGarden() {
		return securityDescriptor.getExecGarden();
	}
	public void setUseLinkButton(boolean useThis) {
		securityDescriptor.setUseLinkButton(useThis);
		settingsChanged = true;
	}

	public boolean isSecureHueApi() {
		return securityDescriptor.isSecureHueApi();
	}
	
	public void setSecureHueApi(boolean theState) {
		securityDescriptor.setSecureHueApi(theState);
	}
	public SecurityInfo getSecurityInfo() {
		SecurityInfo theInfo = new SecurityInfo();
		theInfo.setExecGarden(getExecGarden());
		theInfo.setUseLinkButton(isUseLinkButton());
		theInfo.setSecureHueApi(isSecureHueApi());
		theInfo.setSecure(isSecure());
		return theInfo;
	}
	public boolean validatePassword(User targetUser) throws IOException {
		if(targetUser != null) {
			User theUser = securityDescriptor.getUsers().get(targetUser.getUsername());
			if(theUser.getPassword() != null) {
				theUser.setPassword2(targetUser.getPassword());
				if(theUser.validatePassword()) {
					theUser.setPassword2(null);
					return true;
				}
			} else {
				log.warn("validating password when password is not set....");
				return true;
			}
		}
		return false;
	}
	
	public boolean isSecure() {
		return securityDescriptor.isSecure();
	}

	public boolean isSettingsChanged() {
		return settingsChanged;
	}

	public void setSettingsChanged(boolean settingsChanged) {
		this.settingsChanged = settingsChanged;
	}

	private String encrypt(String property) throws GeneralSecurityException, UnsupportedEncodingException {
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
        SecretKey key = keyFactory.generateSecret(new PBEKeySpec(habridgeKey));
        Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
        pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
        return base64Encode(pbeCipher.doFinal(property.getBytes("UTF-8")));
    }

    private static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String decrypt(String property) throws GeneralSecurityException, IOException {
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
        SecretKey key = keyFactory.generateSecret(new PBEKeySpec(habridgeKey));
        Cipher pbeCipher = Cipher.getInstance("PBEWithMD5AndDES");
        pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
        return new String(pbeCipher.doFinal(base64Decode(property)), "UTF-8");
    }

    private static byte[] base64Decode(String property) throws IOException {
        return Base64.getDecoder().decode(property);
    }
}
