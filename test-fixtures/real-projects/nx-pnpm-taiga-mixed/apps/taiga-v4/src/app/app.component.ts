import {ChangeDetectionStrategy, Component} from '@angular/core';

@Component({
    selector: 'validation-root',
    templateUrl: './app.component.html',
    styleUrl: './app.component.less',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {}
