//
//  CourierWidgetBundle.swift
//  CourierWidget
//
//  Created by Alisher Raximov on 4/25/26.
//

import WidgetKit
import SwiftUI

@main
struct CourierWidgetBundle: WidgetBundle {
    var body: some Widget {
        CourierWidget()
        CourierWidgetControl()
        CourierWidgetLiveActivity()
    }
}
