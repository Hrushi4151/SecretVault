/**
 * WebAuthn / FIDO2 browser utilities for SecretVault.
 * Handles Base64URL conversions, browser capability checks, credential creation / assertion transformations,
 * and user-friendly error formatting.
 *
 * All cryptographic operations and private keys stay strictly within the authenticator hardware.
 * Sensitive ceremony artifacts remain ephemeral in-memory only and are never stored in localStorage/sessionStorage.
 */

/**
 * Checks if the WebAuthn API is supported in the current browser environment.
 */
export function isWebAuthnSupported() {
  return Boolean(
    typeof window !== 'undefined' &&
      window.PublicKeyCredential &&
      typeof window.PublicKeyCredential === 'function' &&
      navigator.credentials &&
      typeof navigator.credentials.create === 'function' &&
      typeof navigator.credentials.get === 'function'
  );
}

/**
 * Checks if a platform authenticator (Touch ID, Windows Hello, Face ID, Android Biometrics) is available.
 */
export async function isPlatformAuthenticatorAvailable() {
  if (!isWebAuthnSupported()) return false;
  try {
    if (typeof PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable === 'function') {
      return await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    }
  } catch (err) {
    console.debug('Error checking platform authenticator availability:', err);
  }
  return false;
}

/**
 * Converts a Base64URL string to a Uint8Array.
 */
export function base64UrlToUint8Array(base64Url) {
  if (!base64Url || typeof base64Url !== 'string') {
    return new Uint8Array(0);
  }
  let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
  while (base64.length % 4 !== 0) {
    base64 += '=';
  }
  const raw = atob(base64);
  const buffer = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    buffer[i] = raw.charCodeAt(i);
  }
  return buffer;
}

/**
 * Converts an ArrayBuffer or Uint8Array to a Base64URL string.
 */
export function arrayBufferToBase64Url(buffer) {
  if (!buffer) return '';
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

/**
 * Transforms server PublicKeyCredentialCreationOptions JSON into browser-ready options for navigator.credentials.create().
 */
export function prepareCreationOptions(serverOptions) {
  const options = typeof serverOptions === 'string' ? JSON.parse(serverOptions) : serverOptions;
  const publicKey = options.publicKey || options;

  const creationOptions = {
    ...publicKey,
    challenge: typeof publicKey.challenge === 'string'
      ? base64UrlToUint8Array(publicKey.challenge)
      : publicKey.challenge,
    user: {
      ...publicKey.user,
      id: typeof publicKey.user.id === 'string'
        ? base64UrlToUint8Array(publicKey.user.id)
        : publicKey.user.id,
    },
  };

  if (Array.isArray(publicKey.excludeCredentials)) {
    creationOptions.excludeCredentials = publicKey.excludeCredentials.map((cred) => ({
      ...cred,
      id: typeof cred.id === 'string' ? base64UrlToUint8Array(cred.id) : cred.id,
    }));
  }

  return { publicKey: creationOptions };
}

/**
 * Transforms server PublicKeyCredentialRequestOptions JSON into browser-ready options for navigator.credentials.get().
 */
export function prepareRequestOptions(serverOptions) {
  const options = typeof serverOptions === 'string' ? JSON.parse(serverOptions) : serverOptions;
  const publicKey = options.publicKey || options;

  const requestOptions = {
    ...publicKey,
    challenge: typeof publicKey.challenge === 'string'
      ? base64UrlToUint8Array(publicKey.challenge)
      : publicKey.challenge,
  };

  if (Array.isArray(publicKey.allowCredentials)) {
    requestOptions.allowCredentials = publicKey.allowCredentials.map((cred) => ({
      ...cred,
      id: typeof cred.id === 'string' ? base64UrlToUint8Array(cred.id) : cred.id,
    }));
  }

  return { publicKey: requestOptions };
}

/**
 * Formats a registration credential created by navigator.credentials.create() into JSON string for backend verification.
 */
export function credentialCreationToJSON(credential) {
  if (!credential) return null;

  const response = credential.response;
  const credentialObj = {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      attestationObject: arrayBufferToBase64Url(response.attestationObject),
      transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
    },
    clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {},
  };

  if (response.getAuthenticatorData) {
    credentialObj.response.authenticatorData = arrayBufferToBase64Url(response.getAuthenticatorData());
  }
  if (response.getPublicKey) {
    credentialObj.response.publicKey = arrayBufferToBase64Url(response.getPublicKey());
  }

  return JSON.stringify(credentialObj);
}

/**
 * Formats an authentication assertion created by navigator.credentials.get() into JSON string for backend verification.
 */
export function credentialRequestToJSON(credential) {
  if (!credential) return null;

  const response = credential.response;
  const credentialObj = {
    id: credential.id,
    rawId: arrayBufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: arrayBufferToBase64Url(response.clientDataJSON),
      authenticatorData: arrayBufferToBase64Url(response.authenticatorData),
      signature: arrayBufferToBase64Url(response.signature),
      userHandle: response.userHandle ? arrayBufferToBase64Url(response.userHandle) : null,
    },
    clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {},
  };

  return JSON.stringify(credentialObj);
}

/**
 * Maps raw browser WebAuthn DOMExceptions to user-friendly, actionable error messages.
 */
export function formatWebAuthnError(error) {
  if (!error) {
    return 'An unexpected WebAuthn error occurred.';
  }

  if (typeof error === 'string') {
    return error;
  }

  switch (error.name) {
    case 'NotAllowedError':
      return 'The authentication ceremony was cancelled or timed out. Please try again.';
    case 'InvalidStateError':
      return 'This passkey or security key is already registered with your account.';
    case 'NotSupportedError':
      return 'Passkeys or the requested authentication algorithm are not supported on this device/browser.';
    case 'SecurityError':
      return 'Security verification failed: The current origin or relying party ID is not permitted.';
    case 'AbortError':
      return 'The authentication request was aborted.';
    case 'ConstraintError':
      return 'The authenticator does not meet the required security constraints.';
    case 'UnknownError':
      return 'An unknown authenticator error occurred. Please reconnect your key or retry.';
    default:
      return error.message || 'WebAuthn verification failed. Please try again.';
  }
}
