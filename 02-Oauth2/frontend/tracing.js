'use strict';

const { NodeSDK } = require('@opentelemetry/sdk-node');
const { getNodeAutoInstrumentations } = require('@opentelemetry/auto-instrumentations-node');
const { OTLPTraceExporter } = require('@opentelemetry/exporter-trace-otlp-grpc');
const { Resource } = require('@opentelemetry/resources');
const { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } = require('@opentelemetry/semantic-conventions');
const { diag, DiagConsoleLogger, DiagLogLevel } = require('@opentelemetry/api');
const { W3CTraceContextPropagator } = require('@opentelemetry/core');
const { W3CBaggagePropagator } = require('@opentelemetry/core');
const { CompositePropagator } = require('@opentelemetry/core');

// Enable diagnostic logging if OTEL_LOG_LEVEL is set
const logLevel = process.env.OTEL_LOG_LEVEL || 'info';
if (logLevel === 'debug') {
  diag.setLogger(new DiagConsoleLogger(), DiagLogLevel.DEBUG);
}

// Service name from env or default
const serviceName = process.env.OTEL_SERVICE_NAME || 'oauth-playground-frontend';
const serviceVersion = process.env.OTEL_SERVICE_VERSION || '1.0.0';

// OTLP gRPC endpoint (e.g., Jaeger, Tempo, or OpenTelemetry Collector)
const otlpEndpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT || 'http://localhost:4317';

console.log(`OpenTelemetry: Initializing tracing for service '${serviceName}'`);
console.log(`OpenTelemetry: OTLP gRPC endpoint: ${otlpEndpoint}`);

const sdk = new NodeSDK({
  resource: new Resource({
    [ATTR_SERVICE_NAME]: serviceName,
    [ATTR_SERVICE_VERSION]: serviceVersion,
  }),
  traceExporter: new OTLPTraceExporter({
    url: otlpEndpoint,
  }),
  // Configure context propagation for distributed tracing
  textMapPropagator: new CompositePropagator({
    propagators: [
      new W3CTraceContextPropagator(),  // Propagates traceparent & tracestate headers
      new W3CBaggagePropagator(),       // Propagates baggage header for custom context
    ],
  }),
  instrumentations: [
    getNodeAutoInstrumentations({
      // Express instrumentation is included by default
      '@opentelemetry/instrumentation-express': {
        enabled: true,
      },
      '@opentelemetry/instrumentation-http': {
        enabled: true,
        // Ensure headers are propagated on outgoing requests
        propagateTraceHeaderCorsUrls: [/.*/],  // Propagate to all URLs
      },
      // Disable file system instrumentation to reduce noise
      '@opentelemetry/instrumentation-fs': {
        enabled: false,
      },
    }),
  ],
});

// Start the SDK
sdk.start();

// Graceful shutdown
process.on('SIGTERM', () => {
  sdk.shutdown()
    .then(() => console.log('OpenTelemetry: Tracing terminated'))
    .catch((error) => console.error('OpenTelemetry: Error terminating tracing', error))
    .finally(() => process.exit(0));
});

console.log('OpenTelemetry: Tracing initialized successfully');
