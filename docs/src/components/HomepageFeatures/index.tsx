import type {ReactNode} from 'react';
import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  emoji: string;
  description: ReactNode;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Background & Foreground Tracking',
    emoji: '📍',
    description: (
      <>
        Foreground service on Android and background modes on iOS, with
        adaptive accuracy and native Kalman filtering to smooth the live
        stream before it reaches JS.
      </>
    ),
  },
  {
    title: 'Native Live Push',
    emoji: '📡',
    description: (
      <>
        Per-fix HTTP POST sent from the native thread, backed by a durable
        SQLite queue — positions keep flowing even while the screen is off or
        the device is in Doze.
      </>
    ),
  },
  {
    title: 'Geofencing & Trip Stats',
    emoji: '🧭',
    description: (
      <>
        Durable enter / exit / dwell geofences that survive reboot, plus
        odometer, ETA, speed alerts, and running trip statistics — all
        computed natively.
      </>
    ),
  },
  {
    title: 'Live Activity & Dynamic Island',
    emoji: '🚗',
    description: (
      <>
        One JS integration drives an iOS 16.2+ Lock Screen / Dynamic Island
        card and an Android ongoing notification for real-time delivery
        status.
      </>
    ),
  },
  {
    title: 'Fake GPS & Network Awareness',
    emoji: '🛡️',
    description: (
      <>
        Detect and reject mock locations, and monitor cellular
        generation / transport changes to react to connectivity shifts.
      </>
    ),
  },
  {
    title: 'Built on Nitro Modules',
    emoji: '⚡',
    description: (
      <>
        JSI-powered HybridObject with a pure C++ math engine for heavy
        trip-math — near-native performance with full TypeScript types.
      </>
    ),
  },
];

function Feature({title, emoji, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4', 'margin-bottom--lg')}>
      <div className={styles.featureCard}>
        <div className={styles.featureIconWrap}>
          <span className={styles.featureEmoji} role="img" aria-hidden="true">
            {emoji}
          </span>
        </div>
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): ReactNode {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
