import { ChangeDetectionStrategy, Component } from '@angular/core';

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
  protected readonly features: readonly Feature[] = [
    {
      label: 'Design token preview',
      title: 'Resolved token values for every theme and platform',
      description:
        'Inspect effective desktop, mobile, light, and dark values together with color swatches and reference chains.',
      image: 'screens/design-token-preview.webp',
      alt: 'WebStorm popup with resolved Taiga UI design token values for desktop, mobile, light, and dark themes',
    },
    {
      label: 'Icon hover preview',
      title: 'See the icon before you use it',
      description: 'Hover a complete @tui icon reference to open a sharp visual preview directly in the editor.',
      image: 'screens/icon-hover-preview.webp',
      alt: 'WebStorm editor showing a Taiga UI icon hover preview',
    },
    {
      label: 'Icon autocomplete',
      title: 'Native completion with a visual preview',
      description:
        'Browse the icon catalog from the project and preview the currently selected icon without leaving code.',
      image: 'screens/icon-autocomplete.webp',
      alt: 'WebStorm autocomplete for Taiga UI icons with a visual preview',
    },
    {
      label: 'Fancy icon autocomplete',
      title: 'Fancy icons are discoverable too',
      description: 'Explore nested fancy icon names with the same project-aware completion and preview experience.',
      image: 'screens/fancy-icon-autocomplete.webp',
      alt: 'WebStorm autocomplete for Taiga UI fancy icons with a currency icon preview',
    },
    {
      label: 'CSS unit hint',
      title: 'Small CSS calculations stay inline',
      description: 'Literal rem values get unobtrusive pixel equivalents in CSS and Angular style bindings.',
      image: 'screens/css-unit-hint.webp',
      alt: 'Angular template with an inline 0.25rem to 4px editor hint',
    },
    {
      label: 'Event plugin completions',
      title: 'Discover ng-event-plugins modifiers from code',
      description:
        'Complete modifiers such as capture, once, prevent, throttle, and zoneless with contextual documentation.',
      image: 'screens/event-plugin-completions.webp',
      alt: 'WebStorm autocomplete for ng-event-plugins modifiers with documentation',
    },
    {
      label: 'Longtap completion',
      title: 'Advanced event plugins are one completion away',
      description:
        'Host bindings get contextual Taiga UI event-plugin suggestions such as longtap and their descriptions.',
      image: 'screens/longtap-completion.webp',
      alt: 'WebStorm host binding autocomplete for the longtap Taiga UI event plugin',
    },
  ];
}
