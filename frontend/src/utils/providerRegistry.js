/**
 * Dynamic Provider Registry for External Platforms & Cloud Infrastructure.
 * Designed to be fully extensible for any existing or future cloud provider.
 */

export const PROVIDER_REGISTRY = {
  VERCEL: {
    id: 'VERCEL',
    displayName: 'Vercel',
    shortName: 'Vercel',
    category: 'Serverless & Frontend',
    iconSymbol: '▲',
    badgeBg: 'bg-black/60',
    badgeBorder: 'border-white/30',
    badgeText: 'text-white',
    accentColor: '#FFFFFF',
    description: 'Serverless deployment and edge runtime platform.',
    resourceLabel: 'Project Name / Deployment',
    defaultResourceType: 'PROJECT',
  },
  RENDER: {
    id: 'RENDER',
    displayName: 'Render',
    shortName: 'Render',
    category: 'Cloud Services & DB',
    iconSymbol: '⬡',
    badgeBg: 'bg-[#46E3B7]/15',
    badgeBorder: 'border-[#46E3B7]/40',
    badgeText: 'text-[#46E3B7]',
    accentColor: '#46E3B7',
    description: 'Unified cloud platform for microservices, web apps, and databases.',
    resourceLabel: 'Web Service / App ID',
    defaultResourceType: 'SERVICE',
  },
  AWS: {
    id: 'AWS',
    displayName: 'Amazon Web Services',
    shortName: 'AWS',
    category: 'Cloud Hyperscaler',
    iconSymbol: '☁',
    badgeBg: 'bg-[#FF9900]/15',
    badgeBorder: 'border-[#FF9900]/40',
    badgeText: 'text-[#FF9900]',
    accentColor: '#FF9900',
    description: 'AWS Secrets Manager & Systems Manager Parameter Store.',
    resourceLabel: 'Secret ARN / Parameter Path',
    defaultResourceType: 'SECRET_STORE',
  },
  AZURE: {
    id: 'AZURE',
    displayName: 'Microsoft Azure',
    shortName: 'Azure',
    category: 'Cloud Hyperscaler',
    iconSymbol: '⚡',
    badgeBg: 'bg-[#0078D4]/15',
    badgeBorder: 'border-[#0078D4]/40',
    badgeText: 'text-[#38BDF8]',
    accentColor: '#0078D4',
    description: 'Azure Key Vault & App Configuration.',
    resourceLabel: 'Vault Name / App Config',
    defaultResourceType: 'KEY_VAULT',
  },
  GCP: {
    id: 'GCP',
    displayName: 'Google Cloud Platform',
    shortName: 'GCP',
    category: 'Cloud Hyperscaler',
    iconSymbol: '🌐',
    badgeBg: 'bg-[#4285F4]/15',
    badgeBorder: 'border-[#4285F4]/40',
    badgeText: 'text-[#60A5FA]',
    accentColor: '#4285F4',
    description: 'Google Cloud Secret Manager & Compute.',
    resourceLabel: 'Secret ID / Project Path',
    defaultResourceType: 'SECRET_STORE',
  },
  KUBERNETES: {
    id: 'KUBERNETES',
    displayName: 'Kubernetes',
    shortName: 'K8s',
    category: 'Containers & Cluster',
    iconSymbol: '☸',
    badgeBg: 'bg-[#326CE5]/15',
    badgeBorder: 'border-[#326CE5]/40',
    badgeText: 'text-[#93C5FD]',
    accentColor: '#326CE5',
    description: 'K8s Cluster Secret Object synchronization.',
    resourceLabel: 'Namespace / Secret Object',
    defaultResourceType: 'CLUSTER_SECRET',
  },
  CLOUDFLARE: {
    id: 'CLOUDFLARE',
    displayName: 'Cloudflare',
    shortName: 'Cloudflare',
    category: 'Edge & CDN',
    iconSymbol: '🟠',
    badgeBg: 'bg-[#F38020]/15',
    badgeBorder: 'border-[#F38020]/40',
    badgeText: 'text-[#FDBA74]',
    accentColor: '#F38020',
    description: 'Cloudflare Workers and Pages environment variables.',
    resourceLabel: 'Worker Name / Pages Project',
    defaultResourceType: 'WORKER',
  },
  RAILWAY: {
    id: 'RAILWAY',
    displayName: 'Railway',
    shortName: 'Railway',
    category: 'PaaS & Containers',
    iconSymbol: '🚂',
    badgeBg: 'bg-[#A855F7]/15',
    badgeBorder: 'border-[#A855F7]/40',
    badgeText: 'text-[#C084FC]',
    accentColor: '#A855F7',
    description: 'Infrastructure platform for microservices and databases.',
    resourceLabel: 'Project ID / Service ID',
    defaultResourceType: 'SERVICE',
  },
  FLY_IO: {
    id: 'FLY_IO',
    displayName: 'Fly.io',
    shortName: 'Fly.io',
    category: 'Edge Containers',
    iconSymbol: '🎈',
    badgeBg: 'bg-[#7C3AED]/15',
    badgeBorder: 'border-[#7C3AED]/40',
    badgeText: 'text-[#DDD6FE]',
    accentColor: '#7C3AED',
    description: 'Deploy app servers close to users with edge secrets.',
    resourceLabel: 'App Name',
    defaultResourceType: 'APP',
  },
  HEROKU: {
    id: 'HEROKU',
    displayName: 'Heroku',
    shortName: 'Heroku',
    category: 'PaaS Platform',
    iconSymbol: '⬢',
    badgeBg: 'bg-[#673AB7]/15',
    badgeBorder: 'border-[#673AB7]/40',
    badgeText: 'text-[#D8B4FE]',
    accentColor: '#673AB7',
    description: 'Heroku Config Vars synchronization.',
    resourceLabel: 'App Name / Pipeline',
    defaultResourceType: 'APP',
  },
  SUPABASE: {
    id: 'SUPABASE',
    displayName: 'Supabase',
    shortName: 'Supabase',
    category: 'Backend as a Service',
    iconSymbol: '⚡',
    badgeBg: 'bg-[#3ECF8E]/15',
    badgeBorder: 'border-[#3ECF8E]/40',
    badgeText: 'text-[#3ECF8E]',
    accentColor: '#3ECF8E',
    description: 'Supabase Project Vault and edge functions env vars.',
    resourceLabel: 'Project Reference ID',
    defaultResourceType: 'PROJECT',
  },
  NETLIFY: {
    id: 'NETLIFY',
    displayName: 'Netlify',
    shortName: 'Netlify',
    category: 'Serverless & Frontend',
    iconSymbol: '◈',
    badgeBg: 'bg-[#00C7B7]/15',
    badgeBorder: 'border-[#00C7B7]/40',
    badgeText: 'text-[#2DD4BF]',
    accentColor: '#00C7B7',
    description: 'Netlify Site environment variables.',
    resourceLabel: 'Site ID / API ID',
    defaultResourceType: 'SITE',
  },
};

/**
 * List of providers available in the "Connect Provider" dialog.
 */
export const AVAILABLE_PROVIDERS_LIST = Object.values(PROVIDER_REGISTRY);

/**
 * Returns provider metadata with full fallback support for ANY future provider.
 * Even if a new platform is added to the backend that isn't in this registry,
 * it will render cleanly without crashing.
 */
export const getProviderMeta = (providerType) => {
  if (!providerType) {
    return {
      id: 'UNKNOWN',
      displayName: 'External Platform',
      shortName: 'External',
      category: 'Cloud Infrastructure',
      iconSymbol: '☁',
      badgeBg: 'bg-[#3F0016]',
      badgeBorder: 'border-[#FFB4C8]/20',
      badgeText: 'text-[#FFB4C8]',
      accentColor: '#FF2D6D',
      description: 'Connected cloud hosting platform.',
      resourceLabel: 'Remote Resource Target',
      defaultResourceType: 'PROJECT',
    };
  }

  const normalized = String(providerType).toUpperCase().replace(/[-\s]/g, '_');
  if (PROVIDER_REGISTRY[normalized]) {
    return PROVIDER_REGISTRY[normalized];
  }

  // Fallback for future or custom external platform
  const formattedName = normalized
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');

  return {
    id: normalized,
    displayName: formattedName,
    shortName: formattedName,
    category: 'Custom Platform',
    iconSymbol: '☁',
    badgeBg: 'bg-[#3F0016]',
    badgeBorder: 'border-[#FFB4C8]/20',
    badgeText: 'text-[#FFB4C8]',
    accentColor: '#FF2D6D',
    description: `${formattedName} cloud infrastructure connector.`,
    resourceLabel: 'Target Resource ID / Name',
    defaultResourceType: 'RESOURCE',
  };
};
