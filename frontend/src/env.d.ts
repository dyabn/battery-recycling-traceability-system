declare module '*.vue' {
  import type { DefineComponent } from 'vue';

  const component: DefineComponent<object, object, unknown>;
  export default component;
}

declare namespace Temporal {
  type Duration = unknown;
  type Instant = unknown;
  type PlainDate = unknown;
  type PlainDateTime = unknown;
  type PlainMonthDay = unknown;
  type PlainTime = unknown;
  type PlainYearMonth = unknown;
  type ZonedDateTime = unknown;
}

declare const Temporal: {
  Duration: unknown;
  Instant: unknown;
  PlainDate: unknown;
  PlainDateTime: unknown;
  PlainMonthDay: unknown;
  PlainTime: unknown;
  PlainYearMonth: unknown;
  ZonedDateTime: unknown;
};
