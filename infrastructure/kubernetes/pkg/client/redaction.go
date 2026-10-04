package client

import (
	"net/http"
	"regexp"
	"strings"
)

var (
	sensitiveHeaders = map[string]bool{
		"authorization":     true,
		"cookie":            true,
		"set-cookie":        true,
		"x-api-key":         true,
		"x-machine-token":   true,
		"x-step-up-proof":   true,
		"proxy-authorization": true,
	}

	jwtRegex            = regexp.MustCompile(`eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+`)
	machineTokenRegex   = regexp.MustCompile(`sv_machine_[A-Za-z0-9_-]+`)
	bearerPrefixRegex   = regexp.MustCompile(`(?i)Bearer\s+[A-Za-z0-9_\-\.]+`)
)

// RedactHeaders returns a deep copy of HTTP headers with all sensitive credential headers replaced by [REDACTED].
func RedactHeaders(h http.Header) http.Header {
	if h == nil {
		return nil
	}
	clean := make(http.Header)
	for k, vv := range h {
		lowerKey := strings.ToLower(k)
		if sensitiveHeaders[lowerKey] {
			clean[k] = []string{"[REDACTED]"}
		} else {
			clean[k] = append([]string(nil), vv...)
		}
	}
	return clean
}

// RedactString scrubs JWT tokens, machine tokens, and Bearer authorization credentials from arbitrary text.
func RedactString(s string) string {
	if s == "" {
		return ""
	}
	res := bearerPrefixRegex.ReplaceAllString(s, "Bearer [REDACTED]")
	res = jwtRegex.ReplaceAllString(res, "[REDACTED_JWT]")
	res = machineTokenRegex.ReplaceAllString(res, "[REDACTED_MACHINE_TOKEN]")
	return res
}
