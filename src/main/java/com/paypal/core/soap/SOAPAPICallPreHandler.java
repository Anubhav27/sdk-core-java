package com.paypal.core.soap;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.paypal.core.APICallPreHandler;
import com.paypal.core.Constants;
import com.paypal.core.CredentialManager;
import com.paypal.core.DefaultSOAPAPICallHandler;
import com.paypal.core.credential.CertificateCredential;
import com.paypal.core.credential.ICredential;
import com.paypal.core.credential.SignatureCredential;
import com.paypal.core.credential.ThirdPartyAuthorization;
import com.paypal.core.credential.TokenAuthorization;
import com.paypal.exception.InvalidCredentialException;
import com.paypal.exception.MissingCredentialException;
import com.paypal.sdk.exceptions.OAuthException;

/**
 * <code>SOAPAPICallPreHandler</code> is an implementation of
 * {@link APICallPreHandler} for SOAP based API service. This serves as a
 * decorator over a basic {@link APICallPreHandler}
 * 
 */
public class SOAPAPICallPreHandler implements APICallPreHandler {

	/**
	 * Pattern for Message Formatting
	 */
	private static final Pattern REGEX_PATTERN = Pattern.compile("(['])");

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
	 * {@link APICallPreHandler} instance
	 */
	private APICallPreHandler apiCallHandler;
	
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

	/**
	 * Internal variable to hold payload
	 */
	private String payLoad;

	/*
	 * Private Constructor
	 */
	private SOAPAPICallPreHandler(APICallPreHandler apiCallHandler) {
		super();
		this.apiCallHandler = apiCallHandler;
	}

	/**
	 * SOAPAPICallPreHandler decorating basic {@link APICallPreHandler} using
	 * API Username
	 * 
	 * @param apiCallHandler
	 *            Instance of {@link APICallPreHandler}
	 * @param apiUserName
	 *            API Username
	 * @param accessToken
	 *            Access Token
	 * @param tokenSecret
	 *            Token Secret
	 * @throws InvalidCredentialException
	 * @throws MissingCredentialException
	 */
	public SOAPAPICallPreHandler(APICallPreHandler apiCallHandler,
			String apiUserName, String accessToken, String tokenSecret)
			throws InvalidCredentialException, MissingCredentialException {
		this(apiCallHandler);
		this.apiUserName = apiUserName;
		this.accessToken = accessToken;
		this.tokenSecret = tokenSecret;
		initCredential();
	}

	/**
	 * SOAPAPICallPreHandler decorating basic {@link APICallPreHandler} using
	 * {@link ICredential}
	 * 
	 * @param apiCallHandler
	 *            Instance of {@link APICallPreHandler}
	 * @param credential
	 *            Instance of {@link ICredential}
	 */
	public SOAPAPICallPreHandler(APICallPreHandler apiCallHandler,
			ICredential credential) {
		this(apiCallHandler);
		if (credential == null) {
			throw new IllegalArgumentException(
					"Credential is null in SOAPAPICallPreHandler");
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
			headers = apiCallHandler.getHeaderMap();
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
			headers.putAll(getDefaultHttpHeadersSOAP());
		}
		return headers;
	}

	public String getPayLoad() {

		// This method appends SOAP Headers to payload
		// if the credentials mandate soap headers
		if (payLoad == null) {
			payLoad = apiCallHandler.getPayLoad();
			String header = null;
			if (credential instanceof SignatureCredential) {
				SignatureCredential sigCredential = (SignatureCredential) credential;
				SignatureSOAPHeaderAuthStrategy signatureSoapHeaderAuthStrategy = new SignatureSOAPHeaderAuthStrategy();
				signatureSoapHeaderAuthStrategy
						.setThirdPartyAuthorization(sigCredential
								.getThirdPartyAuthorization());
				header = signatureSoapHeaderAuthStrategy.generateHeaderStrategy(sigCredential);
			} else if (credential instanceof CertificateCredential) {
				CertificateCredential certCredential = (CertificateCredential) credential;
				CertificateSOAPHeaderAuthStrategy certificateSoapHeaderAuthStrategy = new CertificateSOAPHeaderAuthStrategy();
				certificateSoapHeaderAuthStrategy
						.setThirdPartyAuthorization(certCredential
								.getThirdPartyAuthorization());
				header = certificateSoapHeaderAuthStrategy
						.generateHeaderStrategy(certCredential);

			}
			payLoad = getPayLoadUsingSOAPHeader(payLoad, getNamespaces(),
					header);
		}
		return payLoad;
	}

	public String getEndPoint() {
		return apiCallHandler.getEndPoint();
	}

	public ICredential getCredential() {
		return credential;
	}

	/*
	 * Returns a credential as configured in the application configuration
	 */
	private ICredential getCredentials() throws InvalidCredentialException,
			MissingCredentialException {
		ICredential returnCredential = null;
		CredentialManager credentialManager = CredentialManager.getInstance();
		returnCredential = credentialManager.getCredentialObject(apiUserName);
		if (accessToken != null && accessToken.length() > 0) {

			// Set third party authorization to token
			// if token is sent as part of request call
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

	/*
	 * Returns default HTTP headers used in SOAP call
	 */
	private Map<String, String> getDefaultHttpHeadersSOAP() {
		Map<String, String> returnMap = new HashMap<String, String>();
		returnMap.put(Constants.PAYPAL_REQUEST_DATA_FORMAT_HEADER, "SOAP");
		returnMap.put(Constants.PAYPAL_RESPONSE_DATA_FORMAT_HEADER, "SOAP");
		returnMap.put("X-PAYPAL-REQUEST-SOURCE", sdkName + "-"
				+ sdkVersion);
		return returnMap;
	}

	/*
	 * Initialize {@link ICredential}
	 */
	private void initCredential() throws InvalidCredentialException,
			MissingCredentialException {
		if (credential == null) {
			credential = getCredentials();
		}
	}

	/*
	 * Gets Namespace specific to PayPal APIs
	 */
	private String getNamespaces() {
		String namespace = "xmlns:ns=\"urn:ebay:api:PayPalAPI\" xmlns:ebl=\"urn:ebay:apis:eBLBaseComponents\" xmlns:cc=\"urn:ebay:apis:CoreComponentTypes\" xmlns:ed=\"urn:ebay:apis:EnhancedDataTypes\"";
		return namespace;
	}

	/*
	 * Returns Payload after decoration
	 */
	private String getPayLoadUsingSOAPHeader(String payLoad, String namespace,
			String header) {
		String returnPayLoad = null;
		String formattedPayLoad = processPayLoadForFormatting(payLoad);
		returnPayLoad = MessageFormat.format(formattedPayLoad, new Object[] {
				namespace, header });
		return returnPayLoad;
	}

	/*
	 * Process the payload before using message formatting
	 */
	private String processPayLoadForFormatting(String payLoad) {
		Matcher match = REGEX_PATTERN.matcher(payLoad);
		StringBuffer sb = new StringBuffer();
		while (match.find()) {
			match.appendReplacement(sb, "'" + match.group());
		}
		match.appendTail(sb);
		return sb.toString();
	}

}
