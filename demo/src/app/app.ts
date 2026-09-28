import {ChangeDetectionStrategy, Component, computed, signal} from '@angular/core';

interface Feature {
  readonly label: string;
  readonly title: string;
  readonly description: string;
  readonly image: string;
  readonly alt: string;
}

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  protected readonly activeIndex = signal(0);

  protected readonly features: readonly Feature[] = [
    {
      label: 'Design token hover',
      title: 'Resolved values, variants, and reference chains',
      description:
        'Inspect effective desktop, mobile, light, and dark token values without leaving the editor.',
      image: 'screens/token-hover.webp',
      alt: 'WebStorm popup with resolved Taiga UI design token values for multiple platforms and themes',
    },
    {
      label: 'rem → px hints',
      title: 'Small CSS details, handled inline',
      description:
        'Literal rem values get unobtrusive pixel equivalents in CSS and Angular style bindings.',
      image: 'screens/rem-hint.webp',
      alt: 'Angular template with an inline 0.25rem to 4px editor hint',
    },
  ];

  protected readonly activeFeature = computed(
    () => this.features[this.activeIndex()] ?? this.features[0]!,
  );
}
