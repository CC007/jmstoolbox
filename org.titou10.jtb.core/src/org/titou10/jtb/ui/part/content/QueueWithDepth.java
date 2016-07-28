/*
 * Copyright (C) 2015-2016 Denis Forveille titou10.titou10@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.titou10.jtb.ui.part.content;

import org.titou10.jtb.jms.model.JTBQueue;

/**
 * Holder of a pair Queue / Queue Depth
 * 
 * @author Denis Forveille
 *
 */
final class QueueWithDepth {

   JTBQueue jtbQueue;
   Integer  value;

   QueueWithDepth(JTBQueue jtbQueue, Integer value) {
      this.jtbQueue = jtbQueue;
      this.value = value;
   }

}
