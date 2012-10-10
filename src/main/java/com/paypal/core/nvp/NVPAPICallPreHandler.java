package com.paypal.core.nvp;

import java.util.HashMap;
import java.util.Map;

import com.paypal.core.APICallPreHandler;
import com.paypal.core.ConfigManager;
import com.paypal.core.Constants;
import com.paypal.core.CredentialManager;
import com.paypal.core.credential.CertificateCredential;
import com.paypal.core.credential.ICredential;
import com.paypal.core.credential.ThirdPartyAuthorization;
import com.paypal.core.credential.SignatureCredential;
import com.paypal.core.credential.TokenAuthorization;
import com.paypal.exception.InvalidCredentialException;
import com.paypal.exception.MissingCredentialException;
import com.paypal.sdk.exceptions.OAuthException;

/**
 * <code>NVPAPICallPreHandler</code> is an implementation of
 * {@link APICallPreHandler} for NVP based API service
 * 
 */
public class NVPAPICallPreHandler implements APICallPreHandler {

	/**
	 * Service Name
	 */
	private final String serviceName;

	/**
	 * API method
	 */
	private final String method;

	/**
	 * Raw payload from stubs
	 */
	private final String rawPayLoad;

	/**
	 * API Username for authentication
	 */
	private String apiUserName;

	/**
	 * {@link ICredential} for authentication
	 */
	private ICredential credential;

	/**
	 * Access token if any for authorization
	 */
	private String accessToken;

	/**
	 * TokenSecret if any for authorization
	 */
	private String tokenSecret;

	/**
	 * SDK Name used in tracking
	 */
	private String sdkName;

	/**
	 * SDK Version
	 */
	private String sdkVersion;

	/**
	 * Internal variable to hold headers
	 */
	private Map<String, String> headers;

	// Private Constructor
	private NVPAPICallPreHandler(String rawPayLoad, String serviceName,
			String method) {
		super();
		this.rawPayLoad = rawPayLoad;
		this.serviceName = serviceName;
		this.method = method;
	}

	/**
	 * NVPAPICallPreHandler
	 * 
	 * @param serviceName
	 *            Service Name
	 * @param rawPayLoad
	 *            Payload
	 * @param method
	 *            API method
	 * @param apiUserName
	 *            API Username
	 * @param accessToken
	 *            Access Token
	 * @param tokenSecret
	 *            Token Secret
	 * @throws MissingCredentialException
	 * @throws InvalidCredentialException
	 */
	public NVPAPICallPreHandler(String rawPayLoad, String serviceName,
			String method, String apiUserName, String accessToken,
			String tokenSecret) throws InvalidCredentialException,
			MissingCredentialException {
		this(rawPayLoad, serviceName, method);
		this.apiUserName = apiUserName;
		this.accessToken = accessToken;
		this.tokenSecret = tokenSecret;
		initCredential();
	}

	/**
	 * NVPAPICallPreHandler
	 * 
	 * @param serviceName
	 *            Service Name
	 * @param rawPayLoad
	 *            Payload
	 * @param method
	 *            API method
	 * @param credential
	 *            {@link ICredential} instance
	 */
	public NVPAPICallPreHandler(String rawPayLoad, String serviceName,
			String method, ICredential credential) {
		this(rawPayLoad, serviceName, method);
		if (credential == null) {
			throw new IllegalArgumentException(
					"Credential is null in NVPAPICallPreHandler");
		}
		this.credential = credential;
	}

	/**
	 * @return the sdkName
	 */
	public String getSdkName() {
		return sdkName;
	}

	/**
	 * @param sdkName
	 *            the sdkName to set
	 */
	public void setSdkName(String sdkName) {
		this.sdkName = sdkName;
	}

	/**
	 * @return the sdkVersion
	 */
	public String getSdkVersion() {
		return sdkVersion;
	}

	/**
	 * @param sdkVersion
	 *            the sdkVersion to set
	 */
	public void setSdkVersion(String sdkVersion) {
		this.sdkVersion = sdkVersion;
	}

	public Map<String, String> getHeaderMap() throws OAuthException {
		if (headers == null) {
			headers = new HashMap<String, String>();
			if (credential instanceof SignatureCredential) {
				SignatureHttpHeaderAuthStrategy signatureHttpHeaderAuthStrategy = new SignatureHttpHeaderAuthStrategy(
						getEndPoint());
				headers = signatureHttpHeaderAuthStrategy
						.generateHeaderStrategy((SignatureCredential) credential);
			} else if (credential instanceof CertificateCredential) {
				CertificateHttpHeaderAuthStrategy certificateHttpHeaderAuthStrategy = new CertificateHttpHeaderAuthStrategy(
						getEndPoint());
				headers = certificateHttpHeaderAuthStrategy
						.generateHeaderStrategy((CertificateCredential) credential);
			}
			headers.putAll(getDefaultHttpHeadersNVP());
		}
		return headers;
	}

	public String getPayLoad() {
		// No processing necessary for NVP return the raw payload
		return rawPayLoad;
	}

	public String getEndPoint() {
		return ConfigManager.getInstance().getValue(Constants.END_POINT)
				+ serviceName + "/" + method;
	}

	public ICredential getCredential() {
		return credential;
	}

	private ICredential getCredentials() throws InvalidCredentialException,
			MissingCredentialException {
		ICredential returnCredential = null;
		CredentialManager credentialManager = CredentialManager.getInstance();
		returnCredential = credentialManager.getCredentialObject(apiUserName);
		if (accessToken != null && !accessToken.isEmpty()) {
			ThirdPartyAuthorization tokenAuth = new TokenAuthorization(
					accessToken, tokenSecret);
			if (returnCredential instanceof SignatureCredential) {
				SignatureCredential sigCred = (SignatureCredential) returnCredential;
				sigCred.setThirdPartyAuthorization(tokenAuth);
			} else if (returnCredential instanceof CertificateCredential) {
				CertificateCredential certCred = (CertificateCredential) returnCredential;
				certCred.setThirdPartyAuthorization(tokenAuth);
			}
		}
		return returnCredential;
	}

	private Map<String, String> getDefaultHttpHeadersNVP() {
		Map<String, String> returnMap = new HashMap<String, String>();
		returnMap.put(Constants.PAYPAL_APPLICATION_ID, getApplicationId());
		returnMap.put(Constants.PAYPAL_REQUEST_DATA_FORMAT_HEADER,
				Constants.NVP);
		returnMap.put(Constants.PAYPAL_RESPONSE_DATA_FORMAT_HEADER,
				Constants.NVP);
		returnMap.put("X-PAYPAL-REQUEST-SOURCE", sdkName + "-"
				+ sdkVersion);
		return returnMap;
	}

	private String getApplicationId() {
		String applicationId = null;
		if (credential instanceof CertificateCredential) {
			applicationId = ((CertificateCredential) credential)
					.getApplicationId();
		} else if (credential instanceof SignatureCredential) {
			applicationId = ((SignatureCredential) credential)
					.getApplicationId();
		}
		return applicationId;
	}

	private void initCredential() throws InvalidCredentialException,
			MissingCredentialException {
		if (credential == null) {
			credential = getCredentials();
		}
	}

}
