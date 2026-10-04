package validators

import (
	"testing"
)

func TestSlugValidator(t *testing.T) {
	validSlugs := []string{
		"backend-api",
		"payment-gateway-v2",
		"auth-service",
		"production",
		"stage-01",
		"abc",
	}

	for _, s := range validSlugs {
		if !IsValidSlug(s) {
			t.Errorf("expected %q to be a valid slug", s)
		}
	}

	invalidSlugs := []string{
		"Backend_API",
		"-leading-hyphen",
		"trailing-hyphen-",
		"double--hyphen",
		"has spaces",
		"special@chars!",
	}

	for _, s := range invalidSlugs {
		if IsValidSlug(s) {
			t.Errorf("expected %q to be invalid slug", s)
		}
	}
}

func TestUUIDValidator(t *testing.T) {
	validUUIDs := []string{
		"123e4567-e89b-12d3-a456-426614174000",
		"550e8400-e29b-41d4-a716-446655440000",
		"00000000-0000-0000-0000-000000000000",
		"ffffffff-ffff-ffff-ffff-ffffffffffff",
	}

	for _, u := range validUUIDs {
		if !IsValidUUID(u) {
			t.Errorf("expected %q to be valid UUID", u)
		}
	}

	invalidUUIDs := []string{
		"not-a-uuid",
		"123e4567-e89b-12d3-a456",
		"123e4567-e89b-12d3-a456-4266141740001",
		"123e4567-e89b-12d3-a456_426614174000",
		"",
	}

	for _, u := range invalidUUIDs {
		if IsValidUUID(u) {
			t.Errorf("expected %q to be invalid UUID", u)
		}
	}
}
