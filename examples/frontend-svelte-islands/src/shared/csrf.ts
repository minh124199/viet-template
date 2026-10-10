/**
 * Safe frontend CSRF metadata accessor.
 * Derives CSRF token and header/parameter names from server-rendered DOM metadata.
 *
 * Security Invariant: Presentation token read-only; framework remains authoritative.
 */

export interface CsrfMetadata {
  token: string;
  headerName: string;
  parameterName: string;
}

export function readCsrfMetadata(): CsrfMetadata | null {
  if (typeof document === 'undefined') {
    return null;
  }

  // 1. Check meta tags:
  // e.g. <meta data-vt-csrf-token content="..."> or <meta name="csrf-token" content="...">
  const tokenMeta = document.querySelector<HTMLMetaElement>(
    'meta[data-vt-csrf-token], meta[name="csrf-token"], meta[name="_csrf"]'
  );
  const headerMeta = document.querySelector<HTMLMetaElement>(
    'meta[data-vt-csrf-header], meta[name="csrf-header"], meta[name="_csrf_header"]'
  );
  const paramMeta = document.querySelector<HTMLMetaElement>(
    'meta[data-vt-csrf-param], meta[name="csrf-param"], meta[name="_csrf_param"]'
  );

  let token = tokenMeta?.getAttribute('content') || '';
  let headerName = headerMeta?.getAttribute('content') || '';
  let parameterName = paramMeta?.getAttribute('content') || '';

  // 2. Fall back to hidden input in server-rendered forms if meta is absent
  if (!token) {
    const hiddenInput = document.querySelector<HTMLInputElement>(
      'form input[type="hidden"][name="csrf-token"], form input[type="hidden"][name="_csrf"]'
    );
    if (hiddenInput && hiddenInput.value) {
      token = hiddenInput.value;
      if (!parameterName) {
        parameterName = hiddenInput.name;
      }
    }
  }

  if (!token) {
    return null;
  }

  return {
    token,
    headerName: headerName || 'X-CSRF-TOKEN',
    parameterName: parameterName || 'csrf-token',
  };
}
