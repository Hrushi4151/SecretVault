package validators

import (
	"context"
	"regexp"

	"github.com/hashicorp/terraform-plugin-framework-validators/stringvalidator"
	"github.com/hashicorp/terraform-plugin-framework/schema/validator"
)

var (
	slugRegex       = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)
	secretNameRegex = regexp.MustCompile(`^[A-Za-z0-9_\-\./]+$`)
	uuidRegex       = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
)

// SlugValidator ensures string matches standard URI-safe slug syntax.
func SlugValidator() validator.String {
	return stringvalidator.RegexMatches(
		slugRegex,
		"Must be lowercase alphanumeric with hyphens (e.g., 'backend-prod')",
	)
}

// SecretNameValidator ensures secret key conforms to allowable key naming conventions.
func SecretNameValidator() validator.String {
	return stringvalidator.RegexMatches(
		secretNameRegex,
		"Must consist of alphanumeric characters, hyphens, underscores, dots, or forward slashes",
	)
}

// UUIDValidator ensures the attribute is a valid UUID format.
func UUIDValidator() validator.String {
	return stringvalidator.RegexMatches(
		uuidRegex,
		"Must be a valid UUID format (e.g., '123e4567-e89b-12d3-a456-426614174000')",
	)
}

// IsValidSlug checks if a string is a valid slug.
func IsValidSlug(s string) bool {
	return slugRegex.MatchString(s)
}

// IsValidUUID checks if a string is a valid UUID.
func IsValidUUID(s string) bool {
	return uuidRegex.MatchString(s)
}
